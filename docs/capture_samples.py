"""Сценарий вызовов REST API ResPlan: сохраняет пары «запрос – ответ» в docs/samples/*.json.

Запуск на только что запущенном приложении (профиль demo):
    python docs/capture_samples.py [http://localhost:8080]
"""
import json
import pathlib
import sys
import urllib.error
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:8080'
OUT = pathlib.Path(__file__).resolve().parent / 'samples'
OUT.mkdir(exist_ok=True)
PASSWORD = 'resplan'
tokens = {}
counter = 0


def call(name, method, path, user=None, body=None):
    """Выполняет запрос и сохраняет его вместе с ответом; возвращает тело ответа"""
    global counter
    headers = {'Accept': 'application/json'}
    if user:
        headers['Authorization'] = 'Bearer ' + tokens[user]
    data = None
    if body is not None:
        headers['Content-Type'] = 'application/json'
        data = json.dumps(body, ensure_ascii=False).encode('utf-8')
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req) as r:
            status, reason, resp_headers, raw = r.status, r.reason, dict(r.headers), r.read()
    except urllib.error.HTTPError as e:
        status, reason, resp_headers, raw = e.code, e.reason, dict(e.headers), e.read()
    payload = json.loads(raw) if raw else None
    counter += 1
    record = {
        'request': {'method': method, 'path': path, 'user': user, 'body': body},
        'response': {'status': status, 'reason': reason,
                     'contentType': resp_headers.get('Content-Type'), 'body': payload},
    }
    (OUT / f'{counter:02d}_{name}.json').write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'{counter:02d} {method:6} {path:55} -> {status}')
    return payload


def login(user):
    tokens[user] = call(f'login_{user}', 'POST', '/api/auth/login', body={'login': user, 'password': PASSWORD})['accessToken']


for u in ('pm', 'pm2', 'rm', 'ld', 'emp'):
    login(u)
call('login_wrong_password', 'POST', '/api/auth/login', body={'login': 'pm', 'password': 'qwerty'})
call('me', 'GET', '/api/auth/me', 'pm')
call('no_token', 'GET', '/api/projects')

# UC-1
call('projects', 'GET', '/api/projects', 'pm')
call('tasks', 'GET', '/api/projects/1/tasks', 'pm')
task = call('task_add', 'POST', '/api/projects/1/tasks', 'pm',
            {'name': 'Нагрузочное тестирование', 'durationDays': 4, 'predecessorIds': [7]})
call('task_cycle', 'PUT', '/api/projects/1/tasks/1', 'pm',
     {'name': 'Анализ требований', 'durationDays': 5, 'predecessorIds': [9]})
call('task_invalid', 'POST', '/api/projects/1/tasks', 'pm', {'name': '', 'durationDays': -2})
call('task_foreign_pm', 'PUT', f'/api/projects/1/tasks/{task["id"]}', 'pm2',
     {'name': 'Нагрузочное тестирование', 'durationDays': 5, 'predecessorIds': [7]})
call('schedule', 'GET', '/api/projects/1/schedule', 'pm')

# UC-2
created = call('request_submit', 'POST', '/api/requests', 'pm', {
    'taskId': task['id'], 'role': 'Инженер по нагрузочному тестированию', 'grade': 'Senior', 'loadPercent': 50,
    'startDate': '2026-11-23', 'endDate': '2026-11-27', 'skills': {'Performance testing': 3, 'Test automation': 4}})
call('request_invalid', 'POST', '/api/requests', 'pm', {
    'taskId': task['id'], 'role': '', 'grade': 'Senior', 'loadPercent': 150,
    'startDate': '2026-11-27', 'endDate': '2026-11-23', 'skills': {}})

# UC-5
call('requests_rm', 'GET', '/api/requests?status=SEARCHING', 'rm')
call('candidates', 'GET', '/api/requests/2/candidates', 'rm')
call('proposal_protected', 'POST', '/api/requests/2/proposal', 'rm', {'employeeId': 1})
call('proposal', 'POST', '/api/requests/2/proposal', 'rm', {'employeeId': 7})
call('external_hire', 'POST', f'/api/requests/{created["id"]}/external-hire', 'rm')

# UC-3
call('approval', 'POST', '/api/requests/2/approval', 'pm')
call('approval_again', 'POST', '/api/requests/2/approval', 'pm')
call('rejection_empty', 'POST', '/api/requests/1/rejection', 'pm', {'reason': ''})
call('rejection', 'POST', '/api/requests/1/rejection', 'pm', {'reason': 'Недостаточный опыт с RAG в production'})

# UC-6
conflicts = call('conflicts', 'GET', '/api/conflicts', 'rm')
first = conflicts[0]
call('conflict_resolution', 'POST', f'/api/conflicts/{first["id"]}/resolution', 'rm',
     {'keepBookingId': first['first']['id'], 'note': 'Приоритет проекта ATLAS выше, ORION ищет замену'})
call('conflict_resolution_again', 'POST', f'/api/conflicts/{first["id"]}/resolution', 'rm',
     {'keepBookingId': first['first']['id']})
call('conflict_escalation', 'POST', f'/api/conflicts/{conflicts[1]["id"]}/escalation', 'rm',
     {'note': 'Оба проекта критичны для заказчиков, требуется решение операционного директора'})
call('conflicts_forbidden', 'GET', '/api/conflicts', 'pm')

# UC-7
call('employees_bench', 'GET', '/api/employees?date=2026-10-05&bench=true', 'rm')
call('employee_profile', 'GET', '/api/employees/1?date=2026-10-20', 'rm')
call('bookings_employee', 'GET', '/api/bookings?employeeId=4&from=2026-10-01&to=2026-12-31', 'rm')
booked = call('booking_create', 'POST', '/api/bookings', 'rm', {
    'employeeId': 8, 'projectId': 1, 'kind': 'HARD', 'startDate': '2026-10-12', 'endDate': '2026-10-16', 'loadPercent': 100})
call('booking_protected', 'POST', '/api/bookings', 'rm', {
    'employeeId': 1, 'projectId': 1, 'kind': 'SOFT', 'startDate': '2026-11-16', 'endDate': '2026-11-18', 'loadPercent': 50})
soft = next(b for b in call('bookings_aliev', 'GET', '/api/bookings?employeeId=4&from=2026-10-01&to=2026-10-31', 'rm')
            if b['title'] == 'ORION')
call('booking_harden', 'PATCH', f'/api/bookings/{soft["id"]}', 'rm', {'kind': 'HARD', 'loadPercent': 60})
call('approval_r3_proposal', 'POST', '/api/requests/3/proposal', 'rm', {'employeeId': 10})
r3 = call('approval_r3', 'POST', '/api/requests/3/approval', 'pm2')
call('booking_release', 'DELETE', f'/api/bookings/{r3["request"]["bookingId"]}', 'rm')
call('booking_release_again', 'DELETE', f'/api/bookings/{r3["request"]["bookingId"]}', 'rm')

# UC-8
call('calendar_month', 'GET', '/api/me/calendar?period=MONTH&date=2026-10-15', 'emp')
login('emp2')
call('calendar_bench', 'GET', '/api/me/calendar?period=WEEK&date=2026-10-07', 'emp2')
call('calendar_forbidden', 'GET', '/api/employees/2/calendar', 'emp')

# UC-9
call('courses', 'GET', '/api/courses', 'ld')
training = call('training_reserve', 'POST', '/api/trainings', 'ld', {
    'employeeId': 9, 'courseTitle': 'ISTQB Advanced Test Automation Engineer', 'provider': 'ISTQB',
    'startDate': '2026-11-09', 'endDate': '2026-11-13', 'loadPercent': 100, 'protectedTime': True})
call('training_conflict', 'POST', '/api/trainings', 'ld', {
    'employeeId': 1, 'courseTitle': 'Kubernetes для разработчиков', 'startDate': '2026-10-20',
    'endDate': '2026-10-22', 'loadPercent': 100, 'protectedTime': True})
call('training_cancel', 'DELETE', f'/api/trainings/{training["booking"]["id"]}', 'ld')

# Уведомления
notes = call('notifications', 'GET', '/api/notifications?unread=true', 'pm')
call('notification_read', 'PATCH', f'/api/notifications/{notes[0]["id"]}', 'pm', {'read': True})
print('saved to', OUT)
