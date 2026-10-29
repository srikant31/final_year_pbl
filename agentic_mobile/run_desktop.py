"""Original desktop App with the NEW private key supplied in memory; no original file edits."""
import importlib.util
import ast
import sys
sys.dont_write_bytecode=True
from .pipeline import SOURCE, GEMINI_MODEL, generate_with_fallback, api_key


class DesktopFallbackClient:
    """Use the same bounded cloud policy without changing the professor handler."""
    def __init__(self, root):
        self.models = self
        self.root = root

    def generate_content(self, *, model, contents):
        if model != GEMINI_MODEL:
            raise ValueError('Unexpected primary model in desktop comparison.')
        reply = generate_with_fallback(contents)
        self.root.title(f'Professor Agentic - desktop - {reply.model}')
        return reply


def compatible_handler(namespace):
    """Same teacher method with only the authorized hosted-model keyword changed in memory."""
    tree=ast.parse(SOURCE.read_text(encoding='utf-8'))
    method=next(n for n in ast.walk(tree) if isinstance(n,ast.FunctionDef) and n.name=='classify_image')
    changes=0
    for node in ast.walk(method):
        if isinstance(node,ast.Call) and isinstance(node.func,ast.Attribute) and node.func.attr=='generate_content':
            for keyword in node.keywords:
                if keyword.arg=='model':
                    keyword.value=ast.copy_location(ast.Constant(GEMINI_MODEL),keyword.value)
                    changes+=1
    assert changes==1, 'Teacher call shape changed; inspect instead of silently rewriting it.'
    module=ast.fix_missing_locations(ast.Module(body=[method],type_ignores=[]))
    exec(compile(module,str(SOURCE),'exec'),namespace)
    return namespace['classify_image']

if __name__ == '__main__':
    key=api_key()
    if not key:
        raise SystemExit('Configure the new key using SET_GEMINI_KEY.cmd first. No professor key was used.')
    spec=importlib.util.spec_from_file_location('professor_agentic_desktop',SOURCE)
    original=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(original)
    original.GEMINI_API_KEY=key
    original.App.classify_image=compatible_handler(original.__dict__)
    root=original.tk.Tk()
    app=original.App(root)
    app._gemini.close()
    app._gemini=DesktopFallbackClient(root)
    root.title('Professor Agentic - desktop - Gemini3.8 with3.6 fallback')
    root.mainloop()
