#!/usr/bin/env python3
"""Tests for plan_matrix.py. Run from the repo root: python -I -m unittest discover -s .github/scripts  (or run this file)."""
import json
import os
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import plan_matrix as pm  # noqa: E402

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
SCRIPT = os.path.join(ROOT, ".github", "scripts", "plan_matrix.py")
EVERY = sorted(pm.DIRS)


def dirs(picked):
    return sorted(m[0] for m in picked)


def pair(mc):
    return [f"mc{mc}/forge", f"mc{mc}/neoforge"]


def git_diff(a, b):
    """Changed files between two commits, or skip the test when a commit is missing from this clone."""
    out = subprocess.run(["git", "diff", "--name-only", a, b], cwd=ROOT, capture_output=True, text=True)
    if out.returncode != 0:
        raise unittest.SkipTest(f"commit {a} or {b} is not in this clone")
    return [line for line in out.stdout.splitlines() if line]


def run_script(*args):
    """Runs the script the way the workflows do and returns (exit code, parsed matrix output, any output)."""
    with tempfile.TemporaryDirectory() as tmp:
        out = os.path.join(tmp, "out.txt")
        run = subprocess.run([sys.executable, "-I", SCRIPT, *args], cwd=ROOT, env=dict(os.environ, GITHUB_OUTPUT=out),
                             capture_output=True, text=True)
        values = {}
        if os.path.exists(out):
            with open(out, encoding="utf-8") as fh:
                values = dict(line.rstrip("\n").split("=", 1) for line in fh if "=" in line)
        return run.returncode, json.loads(values["matrix"]) if "matrix" in values else None, values.get("any")


class SyntheticChanges(unittest.TestCase):
    def test_docs_and_status_only_build_nothing(self):
        files = ["README.md", "CHANGELOG.md", "PROJECT_STATUS.md", "LICENSE", "docs/PORTING.md", "branding/banners/banner.png",
                 "curseforge/DESCRIPTION.md", ".gitignore"]
        self.assertEqual(pm.plan_build(files), [])

    def test_workflow_and_ci_script_only_build_nothing(self):
        files = [".github/workflows/build.yml", ".github/workflows/release.yml", ".github/scripts/plan_matrix.py",
                 ".github/scripts/test_plan_matrix.py", ".github/ISSUE_TEMPLATE/config.yml"]
        self.assertEqual(pm.plan_build(files), [])

    def test_logs_and_run_folders_inside_a_build_build_nothing(self):
        files = ["versions/mc26.3/forge/logs/debug-1.log.gz", "versions/mc26.3/forge/run/options.txt",
                 "versions/mc1.20.1/neoforge/logs/2026-11-09-1.log.gz", "versions/mc26.2/forge/.gitignore"]
        self.assertEqual(pm.plan_build(files), [])

    def test_single_build(self):
        self.assertEqual(dirs(pm.plan_build(["versions/mc1.21.1/forge/src/main/java/A.java"])), ["mc1.21.1/forge"])
        self.assertEqual(dirs(pm.plan_build(["versions/mc1.20.1/neoforge/build.gradle", "CHANGELOG.md"])), ["mc1.20.1/neoforge"])
        self.assertEqual(dirs(pm.plan_build(["versions/mc26.1.2/forge/gradle/wrapper/gradle-wrapper.jar"])), ["mc26.1.2/forge"])

    def test_builds_are_independent_so_no_sibling_is_pulled_in(self):
        picked = dirs(pm.plan_build(["versions/mc26.3/forge/src/main/java/A.java"]))
        self.assertEqual(picked, ["mc26.3/forge"])

    def test_two_builds(self):
        files = ["versions/mc26.3/forge/gradle.properties", "versions/mc26.3/neoforge/gradle.properties"]
        self.assertEqual(dirs(pm.plan_build(files)), pair("26.3"))

    def test_unknown_path_selects_everything(self):
        self.assertEqual(dirs(pm.plan_build(["something/new.txt"])), EVERY)
        self.assertEqual(dirs(pm.plan_build([".gitattributes"])), EVERY)
        self.assertEqual(dirs(pm.plan_build(["versions/mc27.0/forge/build.gradle"])), EVERY)
        self.assertEqual(dirs(pm.plan_build(["versions/shared.gradle"])), EVERY)
        self.assertEqual(dirs(pm.plan_build([".github/dependabot.yml"])), EVERY)

    def test_diff_failure_selects_everything(self):
        self.assertEqual(dirs(pm.plan_build(None)), EVERY)
        self.assertIsNone(pm.changed_files("0123456789abcdef0123456789abcdef01234567", "HEAD"))

    def test_matrix_entries_match_the_workflows(self):
        code, matrix, any_ = run_script("build", "--files", "versions/mc1.20.1/forge/build.gradle")
        self.assertEqual(code, 0)
        self.assertEqual(matrix, {"include": [{"dir": "mc1.20.1/forge", "id": "mc1.20.1-forge", "java": 17}]})
        self.assertEqual(any_, "true")
        self.assertEqual(len(pm.ALL), 10)

    def test_files_option_with_nothing_to_build(self):
        code, matrix, any_ = run_script("build", "--files", "PROJECT_STATUS.md")
        self.assertEqual((code, matrix, any_), (0, {"include": []}, "false"))


class RealCommits(unittest.TestCase):
    def test_changelog_only_commit(self):
        self.assertEqual(pm.plan_build(git_diff("916e122^", "916e122")), [])

    def test_workflow_only_commit(self):
        self.assertEqual(pm.plan_build(git_diff("f666a24^", "f666a24")), [])

    def test_branding_only_commit(self):
        self.assertEqual(pm.plan_build(git_diff("a641f80^", "a641f80")), [])

    def test_docs_readme_and_listing_commit(self):
        self.assertEqual(pm.plan_build(git_diff("0e01266^", "0e01266")), [])

    def test_single_build_commits(self):
        self.assertEqual(dirs(pm.plan_build(git_diff("183ba96^", "183ba96"))), ["mc1.21.1/neoforge"])
        self.assertEqual(dirs(pm.plan_build(git_diff("67a425f^", "67a425f"))), ["mc26.1.2/forge"])
        self.assertEqual(dirs(pm.plan_build(git_diff("83c2df1^", "83c2df1"))), ["mc1.21.1/forge"])
        self.assertEqual(dirs(pm.plan_build(git_diff("1b806bd^", "1b806bd"))), ["mc1.20.1/neoforge"])

    def test_one_minecraft_version_commit(self):
        self.assertEqual(dirs(pm.plan_build(git_diff("e67ba96^", "e67ba96"))), pair("26.3"))

    def test_two_minecraft_versions_and_workflows_commit(self):
        self.assertEqual(dirs(pm.plan_build(git_diff("9ffcbee^", "9ffcbee"))), sorted(pair("26.2") + pair("26.3")))

    def test_all_builds_commit(self):
        self.assertEqual(dirs(pm.plan_build(git_diff("44a6f57^", "44a6f57"))), EVERY)

    def test_gitattributes_commit_is_everything(self):
        self.assertEqual(dirs(pm.plan_build(git_diff("e091f66^", "e091f66"))), EVERY)


class ReleaseMode(unittest.TestCase):
    def make_repo(self, versions):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        for m in pm.ALL:
            folder = os.path.join(tmp.name, "versions", m[0])
            os.makedirs(folder)
            with open(os.path.join(folder, "gradle.properties"), "w", encoding="utf-8") as fh:
                fh.write(f"mod_id=bei\nmod_version={versions.get(m[0], '2026.11.15')}\n")
        return tmp.name

    def test_only_builds_at_the_tag_version(self):
        root = self.make_repo({"mc26.3/forge": "2026.11.16", "mc26.3/neoforge": "2026.11.16"})
        version, picked = pm.plan_release("2026.11.16", root)
        self.assertEqual((version, dirs(picked)), ("2026.11.16", pair("26.3")))

    def test_tag_with_a_v_prefix_is_accepted(self):
        root = self.make_repo({"mc26.3/forge": "2026.11.16"})
        self.assertEqual(dirs(pm.plan_release("v2026.11.16", root)[1]), ["mc26.3/forge"])

    def test_all_builds_at_the_tag_release_everything(self):
        root = self.make_repo({})
        self.assertEqual(dirs(pm.plan_release("2026.11.15", root)[1]), EVERY)

    def test_tag_nobody_carries_selects_nothing(self):
        root = self.make_repo({})
        self.assertEqual(pm.plan_release("2026.11.99", root)[1], [])

    def test_release_entries_keep_dir_java_mc_loader(self):
        entry = pm.release_entry(pm.ALL[0])
        self.assertEqual(entry, {"dir": "mc26.3/forge", "java": 25, "mc": "26.3", "loader": "forge"})

    def test_real_repo_current_versions(self):
        current = pm.module_version("mc26.3/forge", ROOT)
        if current is None:
            self.skipTest("mc26.3/forge is not in this checkout")
        version, picked = pm.plan_release(current, ROOT)
        self.assertIn("mc26.3/forge", dirs(picked))

    def test_script_fails_when_no_build_matches_the_tag(self):
        code, matrix, any_ = run_script("release", "--tag", "1999.1.1")
        self.assertNotEqual(code, 0)
        self.assertEqual(any_, "false")


if __name__ == "__main__":
    unittest.main()
