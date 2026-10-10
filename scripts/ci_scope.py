#!/usr/bin/env python3
"""Decides how much of the pipeline a build has to run, from the files a pull request changes.

Used by azure-pipelines.yml. A build of a branch (dev, main, release/*, a tag) always runs
everything. A pull request build runs only what its changes can affect:

  infrastructure checks (stage 2)   when database/, scripts/, Compose or .env.example change
  a service's tests (stage 3)       when files under backend/<service>/ change
  every service that needs infra    when database/ changes, because those suites use the real schema
  the staff console's checks        when files under frontend/ change
  the mobile app's tests            when files under app/ change
  everything                        when the pipeline, this script, backend/pom.xml, Compose,
                                    .env.example or .gitattributes change

It prints Azure Pipelines logging commands that set:

  RUN_INFRA_CHECKS    true or false
  RUN_BACKEND_TESTS   true when at least one service is selected
  RUN_CONSOLE         true or false
  RUN_APP             true or false
  NEED_INFRA          true when a selected service's tests need Oracle, PostgreSQL, Redis or Kafka
  TEST_<service>      true or false, with dashes in the name written as underscores

When it cannot tell what changed, it selects everything. Selecting too much only costs time.

Environment:
  SERVICES_JSON                       the pipeline's services parameter, as JSON
  BUILD_REASON                        "PullRequest" for a pull request build
  SYSTEM_PULLREQUEST_SOURCECOMMITID   the pull request's own commit, used to tell the two
                                      parents of the merge commit apart
"""
import argparse
import json
import os
import subprocess
import sys

# A change to any of these can affect every stage.
EVERYTHING = ("azure-pipelines.yml", "scripts/ci_scope.py", "backend/pom.xml", ".env.example",
              "docker-compose.yml", "docker-compose.target.yml", ".gitattributes")
# A change under any of these is what stage 2 checks.
INFRA_CHECKS = ("database/", "scripts/")
# A change under one of these is what the clients job of stage 3 checks.
CLIENTS = {"CONSOLE": "frontend/", "APP": "app/"}


def git(*args):
    return subprocess.run(["git", *args], capture_output=True, text=True, check=True).stdout


def changed_files(source_commit):
    """Files a pull request changes, read from its merge commit. None when that cannot be worked out."""
    try:
        parents = git("rev-list", "--parents", "-n", "1", "HEAD").split()[1:]
        if len(parents) != 2:
            return None
        # The merge commit has the target branch and the pull request as parents. Its difference
        # from the target branch is exactly what the pull request changes.
        targets = [p for p in parents if p != source_commit] if source_commit in parents else parents
        files = set()
        for parent in targets:  # both parents when the pull request's commit is not known: a superset
            files.update(line for line in git("diff", "--name-only", parent, "HEAD").splitlines() if line)
        return sorted(files)
    except (subprocess.CalledProcessError, OSError):
        return None


def scope(services, files):
    """Returns (run_infra_checks, selected service names, selected clients, reason).

    files is None for 'everything'."""
    names = [s["name"] for s in services]
    if files is None:
        return True, names, list(CLIENTS), "the changed files are not known"
    hit = [f for f in files if f in EVERYTHING]
    if hit:
        return True, names, list(CLIENTS), f"{hit[0]} changed"

    selected = {n for n in names if any(f.startswith(f"backend/{n}/") for f in files)}
    reason = "only what changed"
    if any(f.startswith("database/") for f in files):
        selected |= {s["name"] for s in services if s.get("needsInfra")}
        reason = "database/ changed, so every suite that uses the real schema runs"
    infra_checks = any(f.startswith(INFRA_CHECKS) for f in files)
    clients = [c for c, folder in CLIENTS.items() if any(f.startswith(folder) for f in files)]
    return infra_checks, [n for n in names if n in selected], clients, reason


def self_test():
    services = [{"name": "ledger-service", "needsInfra": True}, {"name": "api-gateway"}]
    everything = (True, ["ledger-service", "api-gateway"], ["CONSOLE", "APP"])
    assert scope(services, None)[:3] == everything
    assert scope(services, ["frontend/src/App.tsx", "azure-pipelines.yml"])[:3] == everything
    assert scope(services, ["frontend/src/App.tsx"])[:3] == (False, [], ["CONSOLE"])
    assert scope(services, ["app/lib/main.dart", "backend/api-gateway/pom.xml"])[:3] == (False, ["api-gateway"], ["APP"])
    assert scope(services, ["database/seed/generate_seed.py"])[:3] == (True, ["ledger-service"], [])
    assert scope(services, ["infra/k8s/config.yaml"])[:3] == (False, [], [])
    print("ci_scope.py: self-test passed.")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--stage-output", action="store_true",
                        help="mark the RUN_ variables as output variables, so a later stage can read them")
    parser.add_argument("--self-test", action="store_true", help="check the rules above and exit")
    args = parser.parse_args()
    if args.self_test:
        return self_test()

    services = json.loads(os.environ["SERVICES_JSON"])
    if os.environ.get("BUILD_REASON") == "PullRequest":
        files = changed_files(os.environ.get("SYSTEM_PULLREQUEST_SOURCECOMMITID", ""))
    else:
        files = None

    infra_checks, selected, clients, reason = scope(services, files)
    if os.environ.get("BUILD_REASON") != "PullRequest":
        reason = "this is not a pull request build"
    need_infra = any(s.get("needsInfra") for s in services if s["name"] in selected)

    print(f"Changed files: {'unknown' if files is None else len(files)}")
    for f in (files or [])[:40]:
        print(f"  {f}")
    print(f"Scope: {reason}.")
    print(f"  infrastructure checks: {'run' if infra_checks else 'skip'}")
    print(f"  service tests: {', '.join(selected) if selected else 'none'}")
    print(f"  clients: {', '.join(clients).lower() if clients else 'none'}")
    print(f"  test infrastructure: {'start' if need_infra else 'not needed'}")

    output = ";isOutput=true" if args.stage_output else ""
    print(f"##vso[task.setvariable variable=RUN_INFRA_CHECKS{output}]{str(infra_checks).lower()}")
    print(f"##vso[task.setvariable variable=RUN_BACKEND_TESTS{output}]{str(bool(selected)).lower()}")
    for c in CLIENTS:
        print(f"##vso[task.setvariable variable=RUN_{c}{output}]{str(c in clients).lower()}")
    print(f"##vso[task.setvariable variable=NEED_INFRA]{str(need_infra).lower()}")
    for s in services:
        flag = str(s["name"] in selected).lower()
        print(f"##vso[task.setvariable variable=TEST_{s['name'].replace('-', '_')}]{flag}")


if __name__ == "__main__":
    sys.exit(main())
