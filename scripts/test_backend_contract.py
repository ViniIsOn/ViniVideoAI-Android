import ast
from pathlib import Path

path = Path("backend/server.py")
tree = ast.parse(path.read_text(encoding="utf-8"))

functions = {
    node.name: node
    for node in tree.body
    if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
}

target = functions.get("set_job")
if target is None or not target.args.args:
    raise SystemExit("set_job function not found")

first_param = target.args.args[0].arg
errors = []

for node in ast.walk(tree):
    if not isinstance(node, ast.Call):
        continue
    if not isinstance(node.func, ast.Name) or node.func.id != "set_job":
        continue
    if not node.args:
        continue
    for kw in node.keywords:
        if kw.arg == first_param:
            errors.append(
                f"Line {node.lineno}: set_job passes '{first_param}' "
                "both positionally and by keyword"
            )

if errors:
    raise SystemExit("\n".join(errors))

print("Backend set_job contract: OK")
