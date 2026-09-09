#!/usr/bin/env python3
"""Compares .igrpstudio DTO models with the Java DTOs. Run from the repo root; exit 1 on drift.

The generator owns application/dto/*.java: a field that exists only in Java is wiped on the next
regeneration, a field that exists only in the model never reached the code. Keep both sides equal.
"""
import glob, json, os, re, sys

FIELD = re.compile(r'^\s*private\s+(?:final\s+)?[\w.<>?, \[\]]+?\s+(\w+)\s*(?:=.*)?;', re.M)
javas = {os.path.basename(p)[:-5]: p for p in glob.glob("src/main/java/**/*.java", recursive=True)}
drift = 0
for model_path in sorted(glob.glob(".igrpstudio/**/dto/*.json", recursive=True)):
    name = os.path.basename(model_path)[:-5]          # file name == Java class (name field may lack the DTO suffix)
    model = [a["name"] for a in json.load(open(model_path))["attributes"]]
    java = javas.get(name)
    if not java:
        print(f"{name}: model without Java class"); drift += 1; continue
    code = FIELD.findall(open(java).read())
    only_code = [f for f in code if f not in model]
    only_model = [f for f in model if f not in code]
    if only_code or only_model:
        drift += 1
        print(f"{name}:")
        if only_code:  print(f"   only in Java : {' '.join(only_code)}")
        if only_model: print(f"   only in model: {' '.join(only_model)}")
modelled = {os.path.basename(p)[:-5] for p in glob.glob(".igrpstudio/**/dto/*.json", recursive=True)}
unmodelled = sorted(n for n, p in javas.items() if "/application/dto/" in p and n.endswith("DTO") and n not in modelled)
if unmodelled:
    drift += 1; print(f"Java DTOs without a model: {' '.join(unmodelled)}")
print("OK — .igrpstudio and code agree" if not drift else f"{drift} drift item(s)")
sys.exit(1 if drift else 0)
