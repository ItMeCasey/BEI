#!/usr/bin/env python3
"""Pick the builds a workflow run needs, so unchanged versions are never built.

  plan_matrix.py build   [--before SHA] [--sha SHA] [--files F ...]   builds affected by the changed files
  plan_matrix.py release --tag YYYY.M.N                               builds whose own mod_version equals the tag

Writes `matrix=<json>` and `any=true|false` to $GITHUB_OUTPUT (and prints them).

How BEI is laid out (checked against every versions/<mc>/<loader>/build.gradle): each build is a fully independent Gradle
project under versions/<mc>/<loader>/. None of them reads a file outside its own folder (no ../common, no shared source set,
no libs folder), so a change under one build's folder selects exactly that build. Logs and run folders inside a build folder
are not compiled in. Docs, markdown, branding, curseforge text, issue templates and CI files select nothing.
An unknown path, or a diff that cannot be computed, selects everything.
"""
import argparse
import json
import os
import re
import subprocess
import sys

# (dir under versions/, id, java, mc, loader)
ALL = [
    ("mc26.3/forge", "mc26.3-forge", 25, "26.3", "forge"),
    ("mc26.3/neoforge", "mc26.3-neoforge", 25, "26.3", "neoforge"),
    ("mc26.2/forge", "mc26.2-forge", 25, "26.2", "forge"),
    ("mc26.2/neoforge", "mc26.2-neoforge", 25, "26.2", "neoforge"),
    ("mc26.1.2/forge", "mc26.1.2-forge", 25, "26.1.2", "forge"),
    ("mc26.1.2/neoforge", "mc26.1.2-neoforge", 25, "26.1.2", "neoforge"),
    ("mc1.21.1/forge", "mc1.21.1-forge", 21, "1.21.1", "forge"),
    ("mc1.21.1/neoforge", "mc1.21.1-neoforge", 21, "1.21.1", "neoforge"),
    ("mc1.20.1/forge", "mc1.20.1-forge", 17, "1.20.1", "forge"),
    ("mc1.20.1/neoforge", "mc1.20.1-neoforge", 17, "1.20.1", "neoforge"),
]
DIRS = [m[0] for m in ALL]
ZERO = "0" * 40

# Root folders that no jar is built from.
NO_BUILD_PREFIXES = (
    "docs/", "branding/", "curseforge/", "reference/", "concept/",
    ".github/ISSUE_TEMPLATE/", ".github/workflows/", ".github/scripts/",
)
NO_BUILD_ROOT_FILES = {".gitignore", "LICENSE", "README.md", "CHANGELOG.md", "PROJECT_STATUS.md", "PLAN.md"}
# Inside a build folder: output, run and log folders and the per-folder ignore file are not compiled in.
NO_BUILD_INSIDE = ("logs/", "run/", "build/", ".gradle/", "run-data/")


def changed_files(before, sha):
    """The files changed between two commits, or None when git cannot tell (then everything is built)."""
    if not before or before == ZERO:
        # first push of a branch or a manual run: compare with the parent commit
        before = sha + "^"
    out = subprocess.run(["git", "diff", "--name-only", before, sha], capture_output=True, text=True)
    if out.returncode != 0:
        return None
    return [line.strip() for line in out.stdout.splitlines() if line.strip()]


def affected(path):
    """The build dirs one changed path affects (a set). Unknown paths affect every build."""
    path = path.replace("\\", "/")
    if path.startswith("./"):
        path = path[2:]
    if path in NO_BUILD_ROOT_FILES or path.startswith(NO_BUILD_PREFIXES):
        return set()
    if "/" not in path and path.endswith(".md"):
        return set()
    parts = path.split("/")
    if len(parts) >= 4 and parts[0] == "versions" and f"{parts[1]}/{parts[2]}" in DIRS:
        inside = "/".join(parts[3:])
        if inside == ".gitignore" or inside.startswith(NO_BUILD_INSIDE) or inside.startswith("run-"):
            return set()
        return {f"{parts[1]}/{parts[2]}"}
    return set(DIRS)


def plan_build(files):
    """The builds for a list of changed files (None means the list is unknown)."""
    if files is None:
        return list(ALL)
    picked = set()
    for f in files:
        picked |= affected(f)
    return [m for m in ALL if m[0] in picked]


def module_version(directory, root="."):
    try:
        with open(os.path.join(root, "versions", directory, "gradle.properties"), encoding="utf-8") as fh:
            text = fh.read()
    except OSError:
        return None
    m = re.search(r"^mod_version=(\S+)", text, re.M)
    return m.group(1) if m else None


def version_key(v):
    return [int(x) for x in v.split(".")]


def plan_release(tag, root="."):
    """The builds whose own mod_version equals the tag (the newest version found when the tag is not a version)."""
    version = (tag or "").lstrip("v")
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        found = {module_version(m[0], root) for m in ALL} - {None}
        version = sorted(found, key=version_key)[-1] if found else ""
    return version, [m for m in ALL if module_version(m[0], root) == version]


def emit(matrix_entries, picked):
    lines = [f"matrix={json.dumps({'include': matrix_entries})}", f"any={'true' if picked else 'false'}"]
    print("\n".join(lines))
    print("builds:", ", ".join(m[0] for m in picked) or "none")
    out = os.environ.get("GITHUB_OUTPUT")
    if out:
        with open(out, "a", encoding="utf-8") as fh:
            fh.write("\n".join(lines) + "\n")


def build_entry(m):
    return {"dir": m[0], "id": m[1], "java": m[2]}


def release_entry(m):
    return {"dir": m[0], "java": m[2], "mc": m[3], "loader": m[4]}


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("mode", choices=["build", "release"])
    ap.add_argument("--before")
    ap.add_argument("--sha", default="HEAD")
    ap.add_argument("--tag")
    ap.add_argument("--files", nargs="*", help="changed files to use instead of asking git (for tests)")
    args = ap.parse_args(argv)

    if args.mode == "release":
        version, picked = plan_release(args.tag)
        for m in ALL:
            print(("Including " if m in picked else "Skipping ") + f"{m[0]} (mod_version {module_version(m[0])}, release {version})")
        emit([release_entry(m) for m in picked], picked)
        if not picked:
            sys.exit(f"No build has mod_version {version}")
    else:
        files = args.files if args.files is not None else changed_files(args.before, args.sha)
        picked = plan_build(files)
        emit([build_entry(m) for m in picked], picked)


if __name__ == "__main__":
    main()
