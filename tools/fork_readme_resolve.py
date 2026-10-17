#!/usr/bin/env python3
"""Auto-resolve the fork's README patches after an upstream merge conflict.

The fork rewrites a handful of upstream-owned README lines: repository links
point at Git-think/MeloX-Android, a fork note links back to the upstream
repository, and the CHKSZ bullet became the Meting + CHKSZ bullet. Upstream
edits some of the same lines on every release (the "Download and full notes"
line always changes), so instead of stopping the sync for a manual merge,
take upstream's newest text and re-apply those rewrites here.

Only README files left conflicted by ``git merge`` are touched; any other
conflicted file still fails the job with a visible error. If the fork's
README edits change, update the transformations below.

Usage: run from the repository root while a merge has conflicts:

    python3 tools/fork_readme_resolve.py
"""

import subprocess
import sys

FILES = ("README.md", "README.en.md", "README.zh-Hans.md")
OLD_OWNER = "lladlam/MeloX-Android"
NEW_OWNER = "Git-think/MeloX-Android"

NOTE_EN = (
    "This repository is the Git-think fork of "
    "[lladlam/MeloX-Android](https://github.com/lladlam/MeloX-Android): "
    "releases here are built from this fork's code and signed with this fork's key."
)
NOTE_ZH = (
    "本仓库是 [lladlam/MeloX-Android](https://github.com/lladlam/MeloX-Android) 的 fork："
    "本仓库的 Release 从这里的代码构建，并使用 fork 自己的签名密钥。"
)
ANCHOR_EN = "native Android port of the design"
ANCHOR_ZH = "进行原生 Android 迁移"

CHKSZ_EN_PREFIX = "- A personal CHKSZ API key can be configured"
CHKSZ_ZH_PREFIX = "- 可配置 CHKSZ 个人 API Key"
METING_EN = (
    "- A Meting API server (metowolf/Meting compatible) can be configured as the default third-party "
    "resolver for NetEase, QQ Music, and Kugou tracks; the CHKSZ resolver from earlier releases is still "
    "available as an optional API key resolver. Enter the Meting server base URL (and auth token if "
    "required), or a CHKSZ API key, under Music Services. Resolution order: Meting → CHKSZ → UNM → LX scripts."
)
METING_ZH = (
    "- 可配置 Meting API（兼容 metowolf/Meting）服务作为默认第三方解析源，优先解析网易云、QQ音乐和酷狗歌曲；"
    "旧版内置的 CHKSZ 解析源保留为可选开关。在「音乐服务」里填写 Meting 服务地址（服务端要求鉴权时再填 Auth 口令），"
    "或配置 CHKSZ 的 API Key。解析顺序：Meting → CHKSZ → UNM → LX 脚本。"
)


def git(*args: str) -> str:
    return subprocess.run(
        ("git",) + args, check=True, capture_output=True, encoding="utf-8"
    ).stdout


def conflicted(path: str) -> bool:
    return bool(git("ls-files", "-u", "--", path).strip())


def resolve(path: str) -> bool:
    if not conflicted(path):
        return False
    text = git("show", f":3:{path}")  # upstream's side of the conflict
    zh = path == "README.zh-Hans.md"
    note, anchor = (NOTE_ZH, ANCHOR_ZH) if zh else (NOTE_EN, ANCHOR_EN)
    meting = METING_ZH if zh else METING_EN
    chksz_prefix = CHKSZ_ZH_PREFIX if zh else CHKSZ_EN_PREFIX

    resolved = []
    for line in text.splitlines():
        if line.startswith(chksz_prefix):
            line = meting
        elif OLD_OWNER in line and "Git-think fork" not in line and "本仓库是" not in line:
            line = line.replace(OLD_OWNER, NEW_OWNER)
        resolved.append(line)

    if note not in resolved:
        for index, line in enumerate(resolved):
            if anchor in line:
                insert_at = index + 1
                if insert_at < len(resolved) and resolved[insert_at].strip() == "":
                    insert_at += 1
                else:
                    resolved.insert(insert_at, "")
                    insert_at += 1
                resolved.insert(insert_at, note)
                insert_at += 1
                if insert_at >= len(resolved) or resolved[insert_at].strip() != "":
                    resolved.insert(insert_at, "")
                break

    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(resolved) + "\n")
    git("add", "--", path)
    return True


def main() -> int:
    changed = [path for path in FILES if resolve(path)]
    for path in changed:
        print(f"resolved {path}")
    if not changed:
        print("no conflicted README files; nothing to resolve")
    return 0


if __name__ == "__main__":
    sys.exit(main())
