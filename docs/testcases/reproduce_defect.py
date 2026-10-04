"""Воспроизведение дефекта D-01: отклонение уже утвержденного кандидата (UC-3).

Запуск из каталога проекта после «mvn package»: python docs/testcases/reproduce_defect.py [before|after];
результат – docs/testcases/defect_D01_<метка>.json (before – сборка до исправления, after – после).
"""
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from run_testcases import Server  # noqa: E402

LABEL = sys.argv[1] if len(sys.argv) > 1 else 'after'
OUT = pathlib.Path(__file__).resolve().parent / f'defect_D01_{LABEL}.json'

with Server() as api:
    steps = []

    def step(name, method, path, user, body=None):
        status, response = api.call(method, path, user, body)
        steps.append({'step': name, 'request': f'{method} {path}', 'status': status, 'response': response})
        print(f'{status} {method} {path}')
        return response

    step('Утверждение кандидата', 'POST', '/api/requests/1/approval', 'pm')
    step('Отклонение утвержденного кандидата', 'POST', '/api/requests/1/rejection', 'pm',
         {'reason': 'Передумали после утверждения'})
    tasks = step('Задачи проекта', 'GET', '/api/projects/1/tasks', 'pm')
    request = step('Карточка запроса', 'GET', '/api/requests/1', 'pm')
    rag = next(t for t in tasks if t['id'] == 5)
    summary = {'requestStatus': request['status'], 'taskAssignee': rag['assignee']}
    print(summary)
    OUT.write_text(json.dumps({'steps': steps, 'summary': summary}, ensure_ascii=False, indent=2), encoding='utf-8')
