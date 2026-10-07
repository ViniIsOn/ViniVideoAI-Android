import ast
import json
import sys
from pathlib import Path

paths = [Path(p) for p in sys.argv[1:]] or [
    Path("colab/ViniVideoAI_FreeGPU.ipynb")
]

all_errors = []

for path in paths:
    data = json.loads(path.read_text(encoding="utf-8"))
    errors = []

    for index, cell in enumerate(data.get("cells", []), start=1):
        source = "".join(cell.get("source", []))

        if cell.get("cell_type") == "markdown":
            if "\\n" in source:
                errors.append(
                    f"Cell {index}: markdown contains literal \\n sequences"
                )
            continue

        if cell.get("cell_type") != "code":
            continue

        code = source

        if code.startswith("%%writefile"):
            parts = code.split("\n", 1)
            code = parts[1] if len(parts) > 1 else ""
        else:
            kept = []
            for line in code.splitlines():
                stripped = line.lstrip()
                if stripped.startswith("!") or stripped.startswith("%"):
                    continue
                kept.append(line)
            code = "\n".join(kept)

        if not code.strip():
            continue

        try:
            ast.parse(code)
        except SyntaxError as exc:
            errors.append(
                f"Cell {index}: Python syntax error at line "
                f"{exc.lineno}: {exc.msg}"
            )

    if errors:
        all_errors.extend(
            [f"{path}: {error}" for error in errors]
        )
    else:
        print(f"{path}: notebook Python cells OK")

if all_errors:
    raise SystemExit("\n".join(all_errors))
