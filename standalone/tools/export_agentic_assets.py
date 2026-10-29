"""Export the unchanged teacher gate, prompt and rule text; never extract credentials."""
import ast,json,re,shutil,hashlib
from pathlib import Path
root=Path(__file__).resolve().parents[2]
tree=ast.parse((root/'Agentic-AI/main.py').read_text(encoding='utf-8'))
functions=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='get_action']
ns={};exec(compile(ast.Module(body=functions,type_ignores=[]),'teacher-actions','exec'),ns)
method=next(n for n in ast.walk(tree) if isinstance(n,ast.FunctionDef) and n.name=='classify_image')
prompt=next(ast.literal_eval(n.value) for n in ast.walk(method) if isinstance(n,ast.Assign) and any(isinstance(t,ast.Name) and t.id=='prompt' for t in n.targets))
model=root/'Agentic-AI/model/spalling_model.tflite'
assert hashlib.sha256(model.read_bytes()).hexdigest()=='520a763549b69b3310e5e082ff5520df03e14eb81bd05646ecb47a98071c747e'
assets=root/'standalone/android/app/src/main/assets';assets.mkdir(parents=True,exist_ok=True)
shutil.copy2(model,assets/'spalling_model.tflite')
actions={d:{s:ns['get_action'](d,s) for s in ['high','medium','low']} for d in ['spalling','crack','void','honeycombing','no defect','unspecified']}
(assets/'agentic_contract.json').write_text(json.dumps({'prompt':prompt,'actions':actions},ensure_ascii=False),encoding='utf-8')
print('Exported original gate/prompt/action assets; no credential extracted.')
