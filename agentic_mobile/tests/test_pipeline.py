"""No live cloud calls or real keys. Compare the actual unchanged teacher handler."""
import ast
import io
import json
from pathlib import Path
import types
import unittest
from unittest.mock import patch
import numpy as np
from PIL import Image
from fastapi.testclient import TestClient
from agentic_mobile.pipeline import (ROOT, SOURCE, FUNCTIONS, PROMPT, GEMINI_MODEL,
    GEMINI_FALLBACK_MODEL, GEMINI_ATTEMPTS, ORIGINAL_GEMINI_MODEL, Pipeline,
    KeyMissing, GeminiReply, GeminiUnavailable, generate_with_fallback)
from agentic_mobile.app import app, pipeline


def original_handler(image, interpreter, reply, compatible=False):
    tree=ast.parse(SOURCE.read_text(encoding='utf-8'))
    fn=next(n for n in ast.walk(tree) if isinstance(n,ast.FunctionDef) and n.name=='classify_image')
    called=[]
    class Client:
        def generate_content(self,**kwargs):
            called.append(kwargs)
            return types.SimpleNamespace(text=reply)
    class Part:
        @staticmethod
        def from_bytes(**kwargs): return kwargs
    ns=dict(FUNCTIONS,np=np,io=io,IMG_SIZE=224,MODEL_NAME='Concrete Vision',
            ACCENT='',SEV_LOW='',SEV_HIGH='',tk=types.SimpleNamespace(X='x'),types=types.SimpleNamespace(Part=Part))
    exec(compile(ast.Module(body=[fn],type_ignores=[]),str(SOURCE),'exec'),ns)
    if compatible:
        from agentic_mobile.run_desktop import compatible_handler
        compatible_handler(ns)
    class Stub:
        current_image=image
        _tflite=interpreter
        _gemini=types.SimpleNamespace(models=Client())
        root=types.SimpleNamespace(update_idletasks=lambda:None)
        progress=types.SimpleNamespace(pack=lambda **k:None,start=lambda *a:None,stop=lambda:None,pack_forget=lambda:None)
        def _write(self,text): self.error=text
        def _set_status(self,*args): pass
        def _ensure_loaded(self): pass
        def _bar(self): return '--'
        def _sev_tag(self,sev): return sev
        def _write_rich(self,segments): self.report=''.join(x[0] for x in segments)
    obj=Stub();ns['classify_image'](obj)
    if not hasattr(obj,'report'):raise AssertionError(getattr(obj,'error','Teacher handler did not complete'))
    return obj.report,called


class FakeInterpreter:
    def __init__(self,score): self.score=score;self.tensor=None
    def set_tensor(self,index,value):self.tensor=value.copy()
    def invoke(self):pass
    def get_tensor(self,index):return np.array([[self.score]],dtype=np.float32)


class PipelineTests(unittest.TestCase):
    def setUp(self):
        self.data=(ROOT/'samples/honeycomb.jpg').read_bytes()
        self.image=Image.open(io.BytesIO(self.data)).convert('RGB')

    def test_both_branches_match_original_method(self):
        for score in [0.1,0.75,0.751,0.99]:
            interpreter=FakeInterpreter(score)
            gate=(interpreter,[{'index':0}],[{'index':0}])
            old,calls=original_handler(self.image,gate,'Defect Type: Honeycombing\nSeverity: Medium')
            new=Pipeline();new._gate=gate
            cloud=[]
            result=new.analyze(self.data,gemini_override=lambda jpeg:cloud.append(jpeg) or 'Defect Type: Honeycombing\nSeverity: Medium')
            expected=np.expand_dims(np.array(self.image.resize((224,224)),dtype=np.float32)/255.0,axis=0)
            np.testing.assert_array_equal(expected,interpreter.tensor)
            self.assertEqual(bool(calls),result['gemini_called'])
            self.assertIn(result['defect'].upper(),old);self.assertIn(result['severity'].upper(),old)
            self.assertIn(result['action'],old)
            if calls:
                self.assertEqual(calls[0]['model'],ORIGINAL_GEMINI_MODEL)
                self.assertEqual(ORIGINAL_GEMINI_MODEL,'gemini-2.5-flash')
                self.assertEqual(GEMINI_MODEL,'gemini-3.8-flash')
                self.assertEqual(calls[0]['contents'][0],PROMPT)
                self.assertEqual(calls[0]['contents'][1]['data'],cloud[0])

    def test_desktop_compatibility_changes_only_hosted_model(self):
        interp=FakeInterpreter(.9);gate=(interp,[{'index':0}],[{'index':0}])
        report,old=original_handler(self.image,gate,'Defect Type: Honeycombing\nSeverity: Medium')
        adapted,new=original_handler(self.image,gate,'Defect Type: Honeycombing\nSeverity: Medium',compatible=True)
        self.assertEqual(report,adapted)
        self.assertEqual(old[0]['contents'],new[0]['contents'])
        self.assertEqual(old[0]['model'],ORIGINAL_GEMINI_MODEL)
        self.assertEqual(new[0]['model'],GEMINI_MODEL)

    def test_missing_key_never_uses_teacher_literal(self):
        from agentic_mobile import pipeline as module
        with patch.object(module,'api_key',return_value=''):
            with self.assertRaises(KeyMissing): Pipeline().analyze(self.data,gate_override=lambda image:0.9)

    def test_real_sdk_request_shape_with_mock_client_only(self):
        from google import genai
        self.assertTrue(hasattr(genai.Client,'__enter__'))
        buf=io.BytesIO();self.image.save(buf,format='JPEG');jpeg=buf.getvalue()
        with patch('agentic_mobile.pipeline.api_key',return_value='test-only-not-a-real-key'),patch('google.genai.Client') as client:
            mock=client.return_value.__enter__.return_value
            mock.models.generate_content.return_value.text='Defect Type: Crack\nSeverity: Low'
            result=Pipeline().gemini(jpeg)
            self.assertIn('Crack',result.text)
            self.assertEqual(result.model,GEMINI_MODEL)
            call=mock.models.generate_content.call_args.kwargs
            self.assertEqual(call['model'],GEMINI_MODEL)
            self.assertEqual(call['contents'][0],PROMPT)
            self.assertEqual(call['contents'][1].inline_data.data,jpeg)
            self.assertEqual(call['contents'][1].inline_data.mime_type,'image/jpeg')

    def test_fallback_timeout_keeps_exact_payload_and_reports_actual_model(self):
        from google.genai.errors import ServerError
        buf=io.BytesIO();self.image.save(buf,format='JPEG');jpeg=buf.getvalue()
        with patch('agentic_mobile.pipeline.api_key',return_value='not-a-real-key'),patch('google.genai.Client') as client:
            mock=client.return_value.__enter__.return_value
            mock.models.generate_content.side_effect=[
                ServerError(504,{'error':{'message':'secret-do-not-leak'}}),
                types.SimpleNamespace(text='Defect Type: Honeycombing\nSeverity: Low')]
            result=Pipeline().analyze(self.data,gate_override=lambda image:.9)
            calls=mock.models.generate_content.call_args_list
            self.assertEqual([c.kwargs['model'] for c in calls],[GEMINI_MODEL,GEMINI_FALLBACK_MODEL])
            self.assertIs(calls[0].kwargs['contents'],calls[1].kwargs['contents'])
            self.assertEqual(calls[1].kwargs['contents'][0],PROMPT)
            self.assertEqual(calls[1].kwargs['contents'][1].inline_data.data,jpeg)
            self.assertEqual(result['gemini_model'],GEMINI_FALLBACK_MODEL)
            self.assertTrue(result['gemini_fallback_used'])
            self.assertEqual(result['defect'],'Honeycombing')
            self.assertEqual([a['status'] for a in result['gemini_attempts']],['http_504','success'])
            options=[c.kwargs['http_options'] for c in client.call_args_list]
            self.assertEqual([o.timeout for o in options],[25000,45000])
            self.assertTrue(all(o.retry_options.attempts==1 for o in options))
            self.assertLess(sum(o.timeout for o in options),150000)

    def test_primary_success_does_not_call_fallback(self):
        with patch('agentic_mobile.pipeline.api_key',return_value='not-a-real-key'),patch('google.genai.Client') as client:
            mock=client.return_value.__enter__.return_value
            mock.models.generate_content.return_value.text='Defect Type: Crack\nSeverity: Low'
            result=Pipeline().analyze(self.data,gate_override=lambda image:.9)
            self.assertEqual(mock.models.generate_content.call_count,1)
            self.assertFalse(result['gemini_fallback_used'])
            self.assertEqual(result['gemini_model'],GEMINI_MODEL)

    def test_availability_errors_allow_fallback(self):
        from google.genai.errors import APIError
        import httpx
        failures=[APIError(code,{'error':{'message':'secret'}}) for code in (404,408,429,500,502,503,504)]
        failures += [TimeoutError('secret'),httpx.ReadTimeout('secret'),httpx.ConnectError('secret')]
        for failure in failures:
            with self.subTest(error=type(failure).__name__,code=getattr(failure,'code',None)):
                with patch('agentic_mobile.pipeline.api_key',return_value='not-a-real-key'),patch('google.genai.Client') as client:
                    mock=client.return_value.__enter__.return_value
                    mock.models.generate_content.side_effect=[failure,types.SimpleNamespace(text='ok')]
                    reply=generate_with_fallback(['same-content'])
                    self.assertEqual(reply.model,GEMINI_FALLBACK_MODEL)
                    self.assertEqual(mock.models.generate_content.call_count,2)

    def test_rejected_or_empty_response_does_not_call_fallback(self):
        from google.genai.errors import ClientError
        for code in (400,401,402,403):
            with self.subTest(code=code),patch('agentic_mobile.pipeline.api_key',return_value='not-a-real-key'),patch('google.genai.Client') as client:
                mock=client.return_value.__enter__.return_value
                mock.models.generate_content.side_effect=ClientError(code,{'error':{'message':'secret'}})
                with self.assertRaises(GeminiUnavailable):generate_with_fallback(['same-content'])
                self.assertEqual(mock.models.generate_content.call_count,1)
        for text in (None,'',' '):
            with self.subTest(text=text),patch('agentic_mobile.pipeline.api_key',return_value='not-a-real-key'),patch('google.genai.Client') as client:
                mock=client.return_value.__enter__.return_value
                mock.models.generate_content.return_value.text=text
                with self.assertRaises(GeminiUnavailable):generate_with_fallback(['same-content'])
                self.assertEqual(mock.models.generate_content.call_count,1)

    def test_both_failures_are_bounded_and_http_error_is_sanitized(self):
        with patch('agentic_mobile.pipeline.api_key',return_value='not-a-real-key'),patch('google.genai.Client') as client,patch.object(pipeline,'gate',return_value=.9):
            mock=client.return_value.__enter__.return_value
            mock.models.generate_content.side_effect=TimeoutError('secret-do-not-leak')
            response=TestClient(app).post('/analyze',files={'image':('h.jpg',self.data)})
            self.assertEqual(response.status_code,502)
            self.assertNotIn('secret-do-not-leak',response.text)
            self.assertIn(GEMINI_MODEL,response.text);self.assertIn(GEMINI_FALLBACK_MODEL,response.text)
            self.assertEqual(mock.models.generate_content.call_count,2)

    def test_desktop_proxy_uses_same_fallback_service(self):
        from agentic_mobile.run_desktop import DesktopFallbackClient
        titles=[];root=types.SimpleNamespace(title=titles.append)
        contents=['original-prompt',b'original-jpeg']
        reply=GeminiReply('Defect Type: Honeycombing\nSeverity: Low',GEMINI_FALLBACK_MODEL,())
        with patch('agentic_mobile.run_desktop.generate_with_fallback',return_value=reply) as generate:
            received=DesktopFallbackClient(root).models.generate_content(model=GEMINI_MODEL,contents=contents)
            self.assertIs(reply,received)
            generate.assert_called_once_with(contents)
            self.assertIn(GEMINI_FALLBACK_MODEL,titles[0])

    def test_key_not_needed_when_gate_skips_cloud(self):
        with patch('agentic_mobile.pipeline.api_key',side_effect=AssertionError('No key lookup expected')):
            result=Pipeline().analyze(self.data,gate_override=lambda image:0.75)
        self.assertFalse(result['gemini_called']);self.assertEqual(result['defect'],'No Defect')

    def test_original_parser_quirks_preserved(self):
        result=Pipeline().analyze(self.data,gate_override=lambda image:0.9,gemini_override=lambda data:'{"defect":"Crack"}')
        self.assertEqual((result['defect'],result['severity']),('Unspecified','Unknown'))

    def test_http_boundaries_and_no_key_leak(self):
        client=TestClient(app)
        self.assertEqual(client.get('/health').status_code,200)
        for data in [b'',b'not an image']:
            self.assertEqual(client.post('/analyze',files={'image':('a.jpg',data)}).status_code,400)
        self.assertEqual(client.post('/analyze').status_code,422)
        with patch.object(pipeline,'gate',return_value=0.5):
            response=client.post('/analyze',files={'image':('h.jpg',self.data)})
        self.assertEqual(response.status_code,200);self.assertFalse(response.json()['gemini_called'])
        with patch.object(pipeline,'gate',return_value=0.9),patch.object(pipeline,'gemini',side_effect=RuntimeError('secret-do-not-leak')):
            response=client.post('/analyze',files={'image':('h.jpg',self.data)})
        self.assertEqual(response.status_code,502);self.assertNotIn('secret-do-not-leak',response.text)

    def test_real_gate_matches_original_handler_samples(self):
        results=[];real=Pipeline()
        for filename in ['crack.jpg','honeycomb.jpg','spalling.jpg','void.jpg']:
            data=(ROOT/'samples'/filename).read_bytes();image=Image.open(io.BytesIO(data)).convert('RGB')
            score=real.gate(image)
            old,calls=original_handler(image,real._gate,'Defect Type: Spalling\nSeverity: High')
            new=real.analyze(data,gemini_override=lambda jpeg:'Defect Type: Spalling\nSeverity: High')
            self.assertAlmostEqual(score,new['gate_score'],places=6)
            self.assertEqual(bool(calls),new['gemini_called'])
            self.assertIn(new['defect'].upper(),old);self.assertIn(new['action'],old)
            results.append({'file':filename,'gate_score':score,'gemini_branch':bool(calls),
                            'cloud_response':'MOCK ONLY; no cloud calls','handler_report_matches':True})
        (ROOT/'agentic_mobile/verification/gate-parity.json').write_text(json.dumps(results,indent=2))


if __name__=='__main__':unittest.main()
