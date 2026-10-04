"""Выполнение тест-кейсов уровня базовых пользовательских требований (UC-1 – UC-9) на запущенном ResPlan.

Каждая группа тест-кейсов одного варианта использования выполняется на новом запуске приложения
с исходными демонстрационными данными (профили postgres,demo: таблицы пересоздаются при старте).
Результат – docs/testcases/results.json: шаги, ожидаемые и фактические результаты, итог по тест-кейсу.

Запуск из каталога проекта после «mvn package»:
    RESPLAN_DB_USER=... RESPLAN_DB_PASSWORD=... python docs/testcases/run_testcases.py
"""
import datetime
import json
import os
import pathlib
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = pathlib.Path(__file__).resolve().parent / 'results.json'
BASE = 'http://localhost:8080'
DB_URL = os.environ.get('RESPLAN_DB_URL', 'jdbc:postgresql://localhost:5432/resplan_lab10')
JAR = next((ROOT / 'target').glob('resplan-backend-*.jar')).relative_to(ROOT).as_posix()
PASSWORD = 'resplan'
ISO_DATE = re.compile(r'\b(\d{4})-(\d{2})-(\d{2})\b')


class Api:
    """Клиент REST API: вход под демонстрационными учетными записями и вызовы с маркером"""

    def __init__(self):
        self.tokens = {}

    def call(self, method, path, user=None, body=None):
        headers = {'Accept': 'application/json'}
        if user:
            if user not in self.tokens:
                self.tokens[user] = self.call('POST', '/api/auth/login',
                                              body={'login': user, 'password': PASSWORD})[1]['accessToken']
            headers['Authorization'] = 'Bearer ' + self.tokens[user]
        data = None
        if body is not None:
            headers['Content-Type'] = 'application/json'
            data = json.dumps(body, ensure_ascii=False).encode('utf-8')
        req = urllib.request.Request(BASE + path, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req) as r:
                status, raw = r.status, r.read()
        except urllib.error.HTTPError as e:
            status, raw = e.code, e.read()
        return status, (json.loads(raw) if raw else None)


class Server:
    """Запуск приложения на чистых демонстрационных данных и его остановка"""

    def __enter__(self):
        env = dict(os.environ)
        self.log = open(ROOT / 'target' / 'testcases-server.log', 'ab')
        # путь к jar относительный: командная строка JVM в Windows искажает не-ASCII символы
        self.proc = subprocess.Popen(
            ['java', '-jar', JAR, '--spring.profiles.active=postgres,demo', f'--spring.datasource.url={DB_URL}'],
            cwd=ROOT, env=env, stdout=self.log, stderr=subprocess.STDOUT)
        for _ in range(120):
            time.sleep(1)
            try:
                with urllib.request.urlopen(BASE + '/v3/api-docs') as r:
                    if r.status == 200:
                        return Api()
            except OSError:
                if self.proc.poll() is not None:
                    raise RuntimeError('Приложение завершилось при запуске, см. target/testcases-server.log')
        raise RuntimeError('Приложение не запустилось за 120 с')

    def __exit__(self, *exc):
        self.proc.terminate()
        self.proc.wait(30)
        self.log.close()


# ---------------------------------------------------------------- описание тест-кейсов

GROUPS = []


def group(uc, title):
    GROUPS.append({'uc': uc, 'title': title, 'cases': []})


def case(case_id, title, requirements, steps):
    """steps – список (действие, ожидаемый результат, проверка); проверка(api, ctx) -> (успех, фактический результат)"""
    GROUPS[-1]['cases'].append({'id': case_id, 'title': title, 'requirements': requirements, 'steps': steps})


def names(items, key='name'):
    return ', '.join(str(i[key]) for i in items)


def problem(status, body):
    detail = body.get('detail', '') if isinstance(body, dict) else ''
    return f'код {status}' + (f', «{detail}»' if detail else '')


# ---------- UC-4
group('UC-4', 'Войти в систему')


def uc4_login(api, ctx):
    s, b = api.call('POST', '/api/auth/login', body={'login': 'pm', 'password': PASSWORD})
    ctx['token'] = b.get('accessToken') if s == 200 else None
    ok = s == 200 and b['tokenType'] == 'Bearer' and b['user']['role'] == 'PM' and b['expiresAt'] and ctx['token']
    return ok, f"код {s}, маркер {b['tokenType']} выдан до {b['expiresAt'][:16].replace('T', ' ')} UTC, роль {b['user']['role']}"


def uc4_me(api, ctx):
    req = urllib.request.Request(BASE + '/api/auth/me', headers={'Authorization': 'Bearer ' + ctx['token']})
    with urllib.request.urlopen(req) as r:
        s, b = r.status, json.loads(r.read())
    ok = s == 200 and b['login'] == 'pm' and b['role'] == 'PM' and b['fullName'] == 'Иванов Дмитрий'
    return ok, f"код {s}, пользователь {b['login']} ({b['fullName']}), роль {b['role']}"


def uc4_wrong(api, ctx):
    s, b = api.call('POST', '/api/auth/login', body={'login': 'pm', 'password': 'qwerty'})
    return s == 401 and b['detail'] == 'Неверные учетные данные' and 'accessToken' not in b, problem(s, b) + ', маркер не выдан'


def uc4_no_token(api, ctx):
    s, b = api.call('GET', '/api/projects')
    return s == 401, problem(s, b)


def uc4_empty(api, ctx):
    s, b = api.call('POST', '/api/auth/login', body={'login': '', 'password': ''})
    fields = sorted(b.get('errors', {}))
    return s == 400 and fields == ['login', 'password'], f"код {s}, ошибочные поля: {', '.join(fields)}"


def uc4_emp_login(api, ctx):
    s, b = api.call('POST', '/api/auth/login', body={'login': 'emp', 'password': PASSWORD})
    return s == 200 and b['user']['role'] == 'EMP', f"код {s}, роль {b['user']['role']} ({b['user']['fullName']})"


def uc4_emp_conflicts(api, ctx):
    s, b = api.call('GET', '/api/conflicts', 'emp')
    return s == 403 and b['detail'] == 'Операция недоступна для роли пользователя', problem(s, b)


def uc4_emp_calendar(api, ctx):
    s, b = api.call('GET', '/api/me/calendar?period=MONTH&date=2026-10-15', 'emp')
    return s == 200 and b['employee'] == 'Ковалёв Алексей', f"код {s}, открыт календарь сотрудника {b['employee']}"


case('UC-4-01', 'Вход в систему с верными учетными данными', ['FR4-1', 'FR4-2', 'FR4-3'], [
    ('Отправить POST /api/auth/login с логином pm и паролем resplan',
     'Код 200; выдан маркер доступа типа Bearer со сроком действия, определена роль PM', uc4_login),
    ('Выполнить GET /api/auth/me с полученным маркером',
     'Код 200; возвращены логин pm, роль PM и ФИО пользователя Иванов Дмитрий', uc4_me),
])
case('UC-4-02', 'Отказ во входе при неверном пароле', ['FR4-4'], [
    ('Отправить POST /api/auth/login с логином pm и паролем qwerty',
     'Код 401; сообщение «Неверные учетные данные», маркер не выдан', uc4_wrong),
    ('Выполнить GET /api/projects без маркера доступа',
     'Код 401; данные проектов не возвращаются', uc4_no_token),
])
case('UC-4-03', 'Отклонение формы входа с незаполненными полями', ['FR4-1'], [
    ('Отправить POST /api/auth/login с пустыми логином и паролем',
     'Код 400; поля login и password отмечены как ошибочные', uc4_empty),
])
case('UC-4-04', 'Предоставление функций в соответствии с ролью пользователя', ['FR4-2'], [
    ('Войти под учетной записью emp (роль «Сотрудник»)',
     'Код 200; определена роль EMP', uc4_emp_login),
    ('Выполнить GET /api/conflicts (панель конфликтов RM)',
     'Код 403; сообщение «Операция недоступна для роли пользователя»', uc4_emp_conflicts),
    ('Выполнить GET /api/me/calendar',
     'Код 200; открыт персональный календарь сотрудника Ковалёв Алексей', uc4_emp_calendar),
])

# ---------- UC-1
group('UC-1', 'Управлять расписанием и сетевым графиком')


def schedule(api, project=1):
    return api.call('GET', f'/api/projects/{project}/schedule', 'pm')


def uc1_projects(api, ctx):
    s, b = api.call('GET', '/api/projects', 'pm')
    codes = [p['code'] for p in b]
    return s == 200 and codes == ['ATLAS', 'ORION'], f"код {s}, проекты: {', '.join(codes)}"


def uc1_card(api, ctx):
    s, b = api.call('GET', '/api/projects/1', 'pm')
    return s == 200 and b['code'] == 'ATLAS' and b['pm'] == 'Иванов Дмитрий', (
        f"код {s}, {b['code']} «{b['name']}», PM {b['pm']}, приоритет {b['priority']}, начало {b['startDate']}")


def uc1_tasks(api, ctx):
    s, b = api.call('GET', '/api/projects/1/tasks', 'pm')
    ctx['tasks'] = len(b)
    linked = sum(1 for t in b if t['predecessorIds'])
    return s == 200 and len(b) == 10, f"код {s}, {len(b)} задач, у {linked} из них заданы предшественники"


def uc1_schedule(api, ctx):
    s, b = schedule(api)
    ok = s == 200 and b['durationDays'] == 34 and len(b['criticalPath']) == 6 and all(r['start'] for r in b['tasks'])
    return ok, (f"код {s}, длительность {b['durationDays']} раб. дней ({b['start']} – {b['finish']}), "
                f"критический путь: {' → '.join(b['criticalPath'])}")


def uc1_add(api, ctx):
    s, b = api.call('POST', '/api/projects/1/tasks', 'pm',
                    {'name': 'Нагрузочное тестирование', 'durationDays': 4, 'predecessorIds': [7]})
    ctx['added'] = b.get('id')
    return s == 201 and b['predecessorIds'] == [7], f"код {s}, задача №{b.get('id')} создана, предшественник – задача №7"


def uc1_add_schedule(api, ctx):
    s, b = schedule(api)
    row = next(r for r in b['tasks'] if r['name'] == 'Нагрузочное тестирование')
    integ = next(r for r in b['tasks'] if r['name'] == 'Интеграция модулей')
    ok = row['es'] == integ['ef'] and row['slack'] == 3 and b['durationDays'] == 34
    return ok, (f"начало {row['start']} (после окончания «Интеграции модулей» {integ['end']}), "
                f"окончание {row['end']}, резерв {row['slack']} дн., длительность проекта {b['durationDays']} дн.")


def uc1_update(api, ctx):
    s, b = api.call('PUT', '/api/projects/1/tasks/4', 'pm',
                    {'name': 'Backend API', 'durationDays': 16, 'predecessorIds': [2]})
    return s == 200 and b['durationDays'] == 16, f"код {s}, длительность задачи {b['durationDays']} дн."


def uc1_update_schedule(api, ctx):
    s, b = schedule(api)
    s2, tasks = api.call('GET', '/api/projects/1/tasks', 'pm')
    saved = next(t for t in tasks if t['id'] == 4)['durationDays']
    ok = b['durationDays'] == 36 and 'Backend API' in b['criticalPath'] and saved == 16
    return ok, (f"длительность проекта {b['durationDays']} дн., окончание {b['finish']}, критический путь: "
                f"{' → '.join(b['criticalPath'])}; в списке задач сохранена длительность {saved} дн.")


def uc1_invalid(api, ctx):
    s, b = api.call('POST', '/api/projects/1/tasks', 'pm', {'name': '', 'durationDays': -2})
    fields = sorted(b.get('errors', {}))
    return s == 400 and fields == ['durationDays', 'name'], f"код {s}, ошибочные поля: {', '.join(fields)}"


def uc1_invalid_count(api, ctx):
    s, b = api.call('GET', '/api/projects/1/tasks', 'pm')
    return len(b) == ctx['tasks'] + 1, f"в проекте {len(b)} задач – столько же, сколько до попытки сохранения"


def uc1_cycle(api, ctx):
    s, b = api.call('PUT', '/api/projects/1/tasks/1', 'pm',
                    {'name': 'Анализ требований', 'durationDays': 5, 'predecessorIds': [9]})
    return s == 422 and b.get('code') == 'CYCLE', problem(s, b) + f", код ошибки {b.get('code')}"


def uc1_cycle_rollback(api, ctx):
    s, b = api.call('GET', '/api/projects/1/tasks', 'pm')
    first = next(t for t in b if t['id'] == 1)
    return first['predecessorIds'] == [], f"у задачи «{first['name']}» предшественников нет, график не изменен"


def uc1_foreign(api, ctx):
    s, b = api.call('PUT', '/api/projects/1/tasks/10', 'pm2',
                    {'name': 'Пользовательская документация', 'durationDays': 5, 'predecessorIds': [6]})
    return s == 403, problem(s, b)


case('UC-1-01', 'Просмотр карточки проекта и сетевого графика', ['FR1-1', 'FR1-3'], [
    ('Войти под учетной записью pm, выполнить GET /api/projects',
     'Код 200; список содержит проекты ATLAS и ORION, которыми руководит pm', uc1_projects),
    ('Открыть карточку проекта ATLAS: GET /api/projects/1',
     'Код 200; код и название проекта, PM Иванов Дмитрий, приоритет и дата начала', uc1_card),
    ('Открыть задачи проекта ATLAS: GET /api/projects/1/tasks',
     'Код 200; 10 задач с длительностями и предшественниками', uc1_tasks),
    ('Открыть сетевой график: GET /api/projects/1/schedule',
     'Код 200; длительность проекта 34 рабочих дня, критический путь из шести задач, даты каждой задачи', uc1_schedule),
])
case('UC-1-02', 'Добавление задачи со связью и пересчет графика', ['FR1-2', 'FR1-3', 'FR1-4'], [
    ('Добавить задачу: POST /api/projects/1/tasks, название «Нагрузочное тестирование», длительность 4, '
     'предшественник – задача №7 «Интеграция модулей»',
     'Код 201; задача сохранена с указанной связью', uc1_add),
    ('Открыть сетевой график проекта',
     'Новая задача начинается после окончания задачи №7, резерв времени 3 дня, длительность проекта не изменилась (34 дня)',
     uc1_add_schedule),
])
case('UC-1-03', 'Пересчет критического пути при изменении длительности задачи', ['FR1-2', 'FR1-3', 'FR1-4'], [
    ('Изменить задачу №4 «Backend API»: PUT /api/projects/1/tasks/4, длительность 16 дней',
     'Код 200; длительность задачи 16 дней', uc1_update),
    ('Открыть сетевой график и список задач проекта',
     'Длительность проекта 36 дней, задача «Backend API» вошла в критический путь, изменение сохранено',
     uc1_update_schedule),
])
case('UC-1-04', 'Блокировка сохранения задачи с некорректными данными', ['FR1-5'], [
    ('Отправить POST /api/projects/1/tasks с пустым названием и длительностью −2',
     'Код 400; поля name и durationDays отмечены как ошибочные', uc1_invalid),
    ('Открыть список задач проекта',
     'Количество задач не изменилось', uc1_invalid_count),
])
case('UC-1-05', 'Отклонение циклической зависимости задач', ['FR1-3', 'FR1-5'], [
    ('Сделать задачу №1 «Анализ требований» последователем задачи №9: PUT /api/projects/1/tasks/1',
     'Код 422; сообщение «Зависимости задач образуют цикл», код ошибки CYCLE', uc1_cycle),
    ('Открыть список задач проекта',
     'Изменение отменено: у задачи №1 нет предшественников', uc1_cycle_rollback),
])
case('UC-1-06', 'Запрет изменения сетевого графика чужим проектным менеджером', ['FR1-4'], [
    ('Войти под учетной записью pm2 и изменить задачу проекта ATLAS: PUT /api/projects/1/tasks/10',
     'Код 403; сообщение «Изменять сетевой график может только PM проекта ATLAS»', uc1_foreign),
])

# ---------- UC-2
group('UC-2', 'Создать запрос на подбор кандидата')

NEW_REQUEST = {'taskId': 10, 'role': 'Технический писатель', 'grade': 'Middle', 'loadPercent': 50,
               'startDate': '2026-11-02', 'endDate': '2026-11-06', 'skills': {'UML': 3, 'BPMN': 3}}


def uc2_free_task(api, ctx):
    s, b = api.call('GET', '/api/projects/1/tasks', 'pm')
    free = [t for t in b if t['assigneeId'] is None]
    doc = next(t for t in free if t['id'] == 10)
    return s == 200 and doc is not None, f"задачи без исполнителя: {names(free)}"


def uc2_submit(api, ctx):
    s, b = api.call('POST', '/api/requests', 'pm', NEW_REQUEST)
    ctx['request'] = b.get('id')
    return s == 201 and b['status'] == 'SEARCHING', (f"код {s}, запрос №{b.get('id')} «{b.get('role')}» по задаче "
                                                     f"«{b.get('task')}», статус {b.get('status')}")


def uc2_rm_notified(api, ctx):
    s, b = api.call('GET', '/api/notifications?unread=true', 'rm')
    msg = next((n['message'] for n in b if n['message'].startswith(f"Новый запрос №{ctx['request']}")), None)
    s2, inbox = api.call('GET', '/api/requests?status=SEARCHING', 'rm')
    return msg is not None and ctx['request'] in [r['id'] for r in inbox], f"уведомление «{msg}»; запрос во входящих RM"


def uc2_invalid(api, ctx):
    s0, before = api.call('GET', '/api/requests', 'pm')
    ctx['count'] = len(before)
    s, b = api.call('POST', '/api/requests', 'pm', {
        'taskId': 10, 'role': '', 'grade': '', 'loadPercent': 150,
        'startDate': '2026-11-06', 'endDate': '2026-11-02', 'skills': {}})
    fields = sorted(b.get('errors', {}))
    expected = ['grade', 'loadPercent', 'periodValid', 'role', 'skills']
    return s == 400 and fields == expected, f"код {s}, ошибочные поля: {', '.join(fields)}"


def uc2_invalid_count(api, ctx):
    s, b = api.call('GET', '/api/requests', 'pm')
    return len(b) == ctx['count'], f"запросов {len(b)}, как и до отправки формы"


def uc2_foreign(api, ctx):
    s, b = api.call('POST', '/api/requests', 'pm2', NEW_REQUEST)
    return s == 403, problem(s, b)


case('UC-2-01', 'Создание запроса на подбор кандидата для задачи без исполнителя', ['FR2-1', 'FR2-2', 'FR2-3'], [
    ('Войти под учетной записью pm, открыть задачи проекта ATLAS',
     'Задача №10 «Пользовательская документация» не имеет исполнителя', uc2_free_task),
    ('Отправить POST /api/requests: задача №10, роль «Технический писатель», грейд Middle, загрузка 50 %, '
     '02.11.2026 – 06.11.2026, навыки UML 3, BPMN 3',
     'Код 201; запрос создан в статусе SEARCHING («Поиск кандидатов»)', uc2_submit),
    ('Войти под учетной записью rm, открыть уведомления и входящие запросы',
     'Получено уведомление о новом запросе, запрос отображается во входящих', uc2_rm_notified),
])
case('UC-2-02', 'Блокировка отправки запроса с незаполненными обязательными полями', ['FR2-4'], [
    ('Отправить POST /api/requests с пустыми ролью и грейдом, загрузкой 150 %, окончанием раньше начала и без навыков',
     'Код 400; поля role, grade, loadPercent, skills и период отмечены как ошибочные', uc2_invalid),
    ('Открыть список запросов PM',
     'Новый запрос не создан', uc2_invalid_count),
])
case('UC-2-03', 'Запрет создания запроса по задаче чужого проекта', ['FR2-1'], [
    ('Войти под учетной записью pm2 и отправить запрос по задаче №10 проекта ATLAS',
     'Код 403; запрос не создан', uc2_foreign),
])

# ---------- UC-5
group('UC-5', 'Просматривать выборку кандидатов по запросу от PM')


def uc5_inbox(api, ctx):
    s, b = api.call('GET', '/api/requests?status=SEARCHING', 'rm')
    items = [f"№{r['id']} {r['role']} ({r['project']})" for r in b]
    return s == 200 and [r['id'] for r in b] == [2, 3], f"код {s}, запросы: {'; '.join(items)}"


def uc5_candidates(api, ctx):
    s, b = api.call('GET', '/api/requests/2/candidates', 'rm')
    matches = [c['match'] for c in b]
    ok = s == 200 and len(b) == 3 and matches == sorted(matches, reverse=True) and b[0]['name'] == 'Ткач Анна'
    return ok, f"код {s}, " + '; '.join(f"{c['name']} – {c['match']} %" for c in b)


CRITERIA = {'skills': 'навыки', 'grade': 'грейд', 'availability': 'доступность', 'cost': 'стоимость'}


def uc5_scores(api, ctx):
    s, b = api.call('GET', '/api/requests/2/candidates', 'rm')
    first = b[0]
    keys = list(first['scores'])
    return keys == ['skills', 'grade', 'availability', 'cost'], (
        'оценки ' + first['name'] + ': ' + ', '.join(f"{CRITERIA[k]} {v:.2f}".replace('.', ',')
                                                     for k, v in first['scores'].items()))


def uc5_propose(api, ctx):
    s, b = api.call('POST', '/api/requests/2/proposal', 'rm', {'employeeId': 7})
    ok = s == 200 and b['status'] == 'PENDING_PM' and b['bookingKind'] == 'SOFT' and b['warnings']
    return ok, f"код {s}, статус {b['status']}, бронь {b['bookingKind']}, предупреждение: {'; '.join(b['warnings'])}"


def uc5_pm_notified(api, ctx):
    s, b = api.call('GET', '/api/notifications?unread=true', 'pm')
    msg = next((n['message'] for n in b if n['message'].startswith('По запросу №2')), None)
    return msg is not None, f"уведомление PM: «{msg}»"


def uc5_rust_request(api, ctx):
    s, b = api.call('POST', '/api/requests', 'pm', dict(NEW_REQUEST, role='MLOps-инженер', grade='Senior',
                                                          skills={'MLOps': 5, 'Kafka': 5, 'Terraform': 5}))
    ctx['rust'] = b.get('id')
    return s == 201, f"код {s}, запрос №{b.get('id')} в статусе {b.get('status')}"


def uc5_empty(api, ctx):
    s, b = api.call('GET', f"/api/requests/{ctx['rust']}/candidates", 'rm')
    return s == 200 and b == [], f"код {s}, список кандидатов пуст"


def uc5_external(api, ctx):
    s, b = api.call('POST', f"/api/requests/{ctx['rust']}/external-hire", 'rm')
    return s == 200 and b['status'] == 'EXTERNAL_HIRE', f"код {s}, статус {b['status']}"


case('UC-5-01', 'Просмотр входящих запросов ресурсного менеджера', ['FR5-1'], [
    ('Войти под учетной записью rm, выполнить GET /api/requests?status=SEARCHING',
     'Код 200; отображаются запросы №2 «QA-инженер» (ATLAS) и №3 «DevOps-инженер» (HELIX)', uc5_inbox),
])
case('UC-5-02', 'Просмотр ранжированного списка кандидатов по запросу', ['FR5-2'], [
    ('Открыть кандидатов по запросу №2: GET /api/requests/2/candidates',
     'Код 200; три кандидата, упорядоченные по убыванию процента совпадения, первый – Ткач Анна', uc5_candidates),
    ('Просмотреть оценки первого кандидата',
     'Указаны оценки по навыкам, грейду, доступности и стоимости', uc5_scores),
])
case('UC-5-03', 'Предложение кандидата проектному менеджеру', ['FR5-2', 'FR3-1'], [
    ('Предложить кандидата Ткач Анна по запросу №2: POST /api/requests/2/proposal',
     'Код 200; статус PENDING_PM, мягкая бронь, предупреждение о перегрузке из-за брони ORION', uc5_propose),
    ('Войти под учетной записью pm, открыть уведомления',
     'Получено уведомление о предложенном кандидате с предупреждением о перегрузке', uc5_pm_notified),
])
case('UC-5-04', 'Передача запроса во внешний найм при пустой выборке', ['FR5-3'], [
    ('Под учетной записью pm создать запрос по задаче №10: роль «MLOps-инженер», грейд Senior, навыки MLOps 5, Kafka 5, Terraform 5',
     'Код 201; запрос создан в статусе SEARCHING', uc5_rust_request),
    ('Под учетной записью rm открыть кандидатов по новому запросу',
     'Код 200; список кандидатов пуст', uc5_empty),
    ('Передать запрос во внешний найм: POST /api/requests/{id}/external-hire',
     'Код 200; статус EXTERNAL_HIRE', uc5_external),
])

# ---------- UC-3
group('UC-3', 'Утвердить кандидата')


def uc3_card(api, ctx):
    s, b = api.call('GET', '/api/requests/1', 'pm')
    ok = s == 200 and b['status'] == 'PENDING_PM' and b['candidate'] == 'Алиев Тимур'
    return ok, f"код {s}, запрос №1 «{b['role']}», статус {b['status']}, кандидат {b['candidate']}"


def uc3_profile(api, ctx):
    s, b = api.call('GET', '/api/employees/4?date=2026-10-20', 'pm')
    skills = ', '.join(f'{k} {v}' for k, v in b['skills'].items())
    return s == 200 and b['skills'], f"код {s}, {b['name']}, {b['grade']}, {b['location']}; навыки: {skills}"


def uc3_approve(api, ctx):
    s, b = api.call('POST', '/api/requests/1/approval', 'pm')
    r = b.get('request', {})
    return s == 200 and r['status'] == 'APPROVED' and r['bookingKind'] == 'HARD', (
        f"код {s}, статус {r.get('status')}, бронь {r.get('bookingKind')}")


def uc3_assigned(api, ctx):
    s, b = api.call('GET', '/api/projects/1/tasks', 'pm')
    task = next(t for t in b if t['id'] == 5)
    s2, notes = api.call('GET', '/api/notifications?unread=true', 'rm')
    msg = next((n['message'] for n in notes if 'утвержден по запросу №1' in n['message']), None)
    return task['assignee'] == 'Алиев Тимур' and msg, f"исполнитель задачи «{task['name']}» – {task['assignee']}; RM: «{msg}»"


def uc3_again(api, ctx):
    s, b = api.call('POST', '/api/requests/1/approval', 'pm')
    return s == 409, problem(s, b)


def uc3_reject_approved(api, ctx):
    s, b = api.call('POST', '/api/requests/1/rejection', 'pm', {'reason': 'Передумали после утверждения'})
    return s == 409, problem(s, b)


def uc3_still_approved(api, ctx):
    s, r = api.call('GET', '/api/requests/1', 'pm')
    s2, tasks = api.call('GET', '/api/projects/1/tasks', 'pm')
    task = next(t for t in tasks if t['id'] == 5)
    return r['status'] == 'APPROVED' and task['assignee'] == 'Алиев Тимур', (
        f"статус запроса {r['status']}, бронь {r['bookingKind']}; исполнитель задачи «{task['name']}» – {task['assignee']}")


def uc3_prepare(api, ctx):
    s, b = api.call('POST', '/api/requests/2/proposal', 'rm', {'employeeId': 7})
    return s == 200 and b['status'] == 'PENDING_PM', f"код {s}, запрос №2 в статусе {b['status']}, кандидат {b['candidate']}"


def uc3_reject_empty(api, ctx):
    s, b = api.call('POST', '/api/requests/2/rejection', 'pm', {'reason': ''})
    s2, r = api.call('GET', '/api/requests/2', 'pm')
    return s == 422 and b.get('code') == 'REASON' and r['status'] == 'PENDING_PM', (
        problem(s, b) + f"; статус запроса {r['status']}")


def uc3_reject(api, ctx):
    s, b = api.call('POST', '/api/requests/2/rejection', 'pm', {'reason': 'Занята на ORION до 13.11, нужен полный выход'})
    return s == 200 and b['status'] == 'SEARCHING' and b['candidate'] is None, f"код {s}, статус {b['status']}, бронь снята"


def uc3_reject_effect(api, ctx):
    s, b = api.call('GET', '/api/requests/2/candidates', 'rm')
    s2, notes = api.call('GET', '/api/notifications?unread=true', 'rm')
    msg = next((n['message'] for n in notes if 'отклонен по запросу №2' in n['message']), None)
    return 'Ткач Анна' not in [c['name'] for c in b] and msg, f"кандидаты: {names(b)}; RM: «{msg}»"


case('UC-3-01', 'Просмотр карточки запроса и профиля предложенного кандидата', ['FR3-1'], [
    ('Войти под учетной записью pm, открыть запрос №1: GET /api/requests/1',
     'Код 200; статус PENDING_PM, предложен кандидат Алиев Тимур', uc3_card),
    ('Открыть профиль кандидата: GET /api/employees/4',
     'Код 200; грейд, локация и матрица навыков кандидата', uc3_profile),
])
case('UC-3-02', 'Утверждение кандидата с установкой жесткой брони', ['FR3-2'], [
    ('Утвердить кандидата: POST /api/requests/1/approval',
     'Код 200; статус APPROVED, бронь кандидата стала жесткой (HARD)', uc3_approve),
    ('Открыть задачи проекта ATLAS и уведомления RM',
     'Исполнителем задачи «RAG-модуль GenAI» назначен Алиев Тимур, RM получил уведомление', uc3_assigned),
])
case('UC-3-03', 'Запрет повторного утверждения кандидата', ['FR3-2'], [
    ('Повторно отправить POST /api/requests/1/approval',
     'Код 409; сообщение о недопустимом переходе из статуса APPROVED', uc3_again),
])
case('UC-3-04', 'Запрет отклонения уже утвержденного кандидата', ['FR3-2', 'FR3-3'], [
    ('Отклонить утвержденного кандидата по запросу №1 с причиной «Передумали после утверждения»',
     'Код 409; сообщение о недопустимом переходе из статуса APPROVED', uc3_reject_approved),
    ('Открыть карточку запроса №1 и задачи проекта ATLAS',
     'Запрос остается в статусе APPROVED, исполнитель задачи «RAG-модуль GenAI» – Алиев Тимур', uc3_still_approved),
])
case('UC-3-05', 'Отклонение кандидата без указания причины', ['FR3-3'], [
    ('Под учетной записью rm предложить кандидата Ткач Анна по запросу №2',
     'Код 200; запрос №2 в статусе PENDING_PM', uc3_prepare),
    ('Под учетной записью pm отклонить кандидата с пустой причиной: POST /api/requests/2/rejection',
     'Код 422; сообщение «Необходимо указать причину отклонения», статус запроса не изменился', uc3_reject_empty),
])
case('UC-3-06', 'Отклонение кандидата с указанием причины', ['FR3-3'], [
    ('Отклонить кандидата по запросу №2 с причиной «Занята на ORION до 13.11, нужен полный выход»',
     'Код 200; запрос возвращен в статус SEARCHING, бронь кандидата снята', uc3_reject),
    ('Под учетной записью rm открыть кандидатов по запросу №2 и уведомления',
     'Отклоненный кандидат исключен из выборки, RM получил уведомление с причиной', uc3_reject_effect),
])

# ---------- UC-6
group('UC-6', 'Разрешить ресурсный конфликт')


def uc6_panel(api, ctx):
    s, b = api.call('GET', '/api/conflicts', 'rm')
    ctx['conflicts'] = b
    rows = [f"№{c['id']} {c['employee']}: {c['bookingA']} (приоритет {c['first']['projectPriority']}) и "
            f"{c['bookingB']} (приоритет {c['second']['projectPriority']}), {c['totalLoad']} %" for c in b]
    return s == 200 and len(b) == 2 and all(c['status'] == 'OPEN' for c in b), f"код {s}; " + '; '.join(rows)


def uc6_card(api, ctx):
    s, b = api.call('GET', '/api/conflicts/1', 'rm')
    return s == 200 and b['from'] == '2026-10-19' and b['to'] == '2026-10-27', (
        f"код {s}, пересечение {b['from']} – {b['to']}: {b['bookingA']} {b['first']['loadPercent']} % и "
        f"{b['bookingB']} {b['second']['loadPercent']} %")


def uc6_resolve(api, ctx):
    c = ctx['conflicts'][0]
    s, b = api.call('POST', f"/api/conflicts/{c['id']}/resolution", 'rm',
                    {'keepBookingId': c['first']['id'], 'note': 'Приоритет ATLAS выше, ORION ищет замену'})
    s2, bookings = api.call('GET', '/api/bookings?employeeId=1&from=2026-10-01&to=2026-11-30', 'rm')
    titles = [x['title'] for x in bookings if x['type'] == 'PROJECT']
    return s == 200 and b['status'] == 'RESOLVED' and titles == ['ATLAS'], (
        f"код {s}, статус {b['status']}; действующие проектные брони сотрудника: {', '.join(titles)}")


def uc6_pm_notified(api, ctx):
    s, b = api.call('GET', '/api/notifications?unread=true', 'pm')
    msg = next((n['message'] for n in b if n['message'].startswith('Конфликт №1')), None)
    return msg is not None, f"уведомление PM: «{msg}»"


def uc6_escalate_empty(api, ctx):
    s, b = api.call('POST', '/api/conflicts/2/escalation', 'rm', {'note': ''})
    return s == 400 and 'note' in b.get('errors', {}), f"код {s}, ошибочное поле: {', '.join(b.get('errors', {}))}"


def uc6_escalate(api, ctx):
    s, b = api.call('POST', '/api/conflicts/2/escalation', 'rm',
                    {'note': 'Оба проекта критичны для заказчиков, требуется решение операционного директора'})
    s2, n2 = api.call('GET', '/api/notifications?unread=true', 'pm2')
    msg = next((n['message'] for n in n2 if 'эскалирован' in n['message']), None)
    return s == 200 and b['status'] == 'ESCALATED' and msg, f"код {s}, статус {b['status']}; PM pm2: «{msg}»"


def uc6_closed(api, ctx):
    c = ctx['conflicts'][1]
    s, b = api.call('POST', f"/api/conflicts/{c['id']}/resolution", 'rm', {'keepBookingId': c['first']['id']})
    return s == 422 and b.get('code') == 'CONFLICT_CLOSED', problem(s, b)


case('UC-6-01', 'Просмотр панели ресурсных конфликтов', ['FR6-1'], [
    ('Войти под учетной записью rm, выполнить GET /api/conflicts',
     'Код 200; два открытых конфликта с пересекающимися бронями, приоритетами проектов и суммарной загрузкой',
     uc6_panel),
    ('Открыть карточку конфликта №1: GET /api/conflicts/1',
     'Код 200; период пересечения 19.10.2026 – 27.10.2026 и загрузка по каждой брони', uc6_card),
])
case('UC-6-02', 'Разрешение конфликта в пользу приоритетного проекта', ['FR6-2', 'FR6-3'], [
    ('Разрешить конфликт №1, сохранив бронь ATLAS: POST /api/conflicts/1/resolution',
     'Код 200; конфликт в статусе RESOLVED, бронь ORION снята', uc6_resolve),
    ('Войти под учетной записью pm, открыть уведомления',
     'PM получил уведомление о принятом решении', uc6_pm_notified),
])
case('UC-6-03', 'Отклонение эскалации без указания причины', ['FR6-4'], [
    ('Отправить POST /api/conflicts/2/escalation с пустой причиной',
     'Код 400; поле note отмечено как ошибочное', uc6_escalate_empty),
])
case('UC-6-04', 'Эскалация неразрешимого конфликта операционному директору', ['FR6-4', 'FR6-3'], [
    ('Эскалировать конфликт №2 с указанием причины: POST /api/conflicts/2/escalation',
     'Код 200; статус ESCALATED, PM обоих проектов получили уведомление', uc6_escalate),
    ('Попытаться разрешить эскалированный конфликт: POST /api/conflicts/2/resolution',
     'Код 422; сообщение о том, что конфликт уже имеет статус ESCALATED', uc6_closed),
])

# ---------- UC-7
group('UC-7', 'Управлять бронированием кандидатов')


def uc7_pool(api, ctx):
    s, b = api.call('GET', '/api/employees?date=2026-10-05&bench=true', 'rm')
    return s == 200 and all(e['bench'] for e in b) and 'Ковалёв Алексей' in names(b), (
        f"код {s}, в резерве {len(b)} сотрудников, в том числе: {names(b[:4])}")


def uc7_profile(api, ctx):
    s, b = api.call('GET', '/api/employees/1?date=2026-10-20', 'rm')
    return s == 200 and b['loadPercent'] == 150, (
        f"код {s}, {b['name']}: загрузка {b['loadPercent']} %, брони {names(b['bookings'], 'title')}")


def uc7_hard(api, ctx):
    s, b = api.call('POST', '/api/bookings', 'rm', {'employeeId': 11, 'projectId': 2, 'kind': 'HARD',
                                                     'startDate': '2026-10-12', 'endDate': '2026-10-23', 'loadPercent': 50})
    ctx['booking'] = b['booking']['id'] if s == 201 else None
    bk = b.get('booking', {})
    return s == 201 and bk['kind'] == 'HARD' and not b['conflicts'], (
        f"код {s}, бронь №{bk.get('id')} {bk.get('kind')} {bk.get('title')}, {bk.get('startDate')} – {bk.get('endDate')}, "
        f"{bk.get('loadPercent')} %")


def uc7_hard_effect(api, ctx):
    s, b = api.call('GET', '/api/employees/11?date=2026-10-14', 'rm')
    return b['loadPercent'] == 50 and not b['bench'], f"загрузка {b['name']} на 14.10.2026 – {b['loadPercent']} %"


def uc7_overload(api, ctx):
    s, b = api.call('POST', '/api/bookings', 'rm', {'employeeId': 8, 'projectId': 1, 'kind': 'HARD',
                                                     'startDate': '2026-10-12', 'endDate': '2026-10-16', 'loadPercent': 100})
    return s == 201 and len(b['conflicts']) == 1 and b['warnings'], (
        f"код {s}; {'; '.join(b['warnings'])}; зарегистрирован конфликт №{b['conflicts'][0]['id']} "
        f"({b['conflicts'][0]['totalLoad']} %)")


def uc7_harden(api, ctx):
    s0, lst = api.call('GET', '/api/bookings?employeeId=4&from=2026-10-01&to=2026-10-31', 'rm')
    soft = next(x for x in lst if x['title'] == 'ORION')
    s, b = api.call('PATCH', f"/api/bookings/{soft['id']}", 'rm', {'kind': 'HARD', 'loadPercent': 60})
    bk = b.get('booking', {})
    return soft['kind'] == 'SOFT' and s == 200 and bk['kind'] == 'HARD' and bk['loadPercent'] == 60, (
        f"бронь №{soft['id']} ORION: было {soft['kind']} {soft['loadPercent']} %, стало {bk.get('kind')} "
        f"{bk.get('loadPercent')} % (код {s})")


def uc7_protected(api, ctx):
    s, b = api.call('POST', '/api/bookings', 'rm', {'employeeId': 1, 'projectId': 1, 'kind': 'SOFT',
                                                     'startDate': '2026-11-16', 'endDate': '2026-11-18', 'loadPercent': 50})
    return s == 422 and b.get('code') == 'PROTECTED_TIME', problem(s, b)


def uc7_release(api, ctx):
    s0, lst = api.call('GET', '/api/bookings?employeeId=11&from=2026-10-05&to=2026-10-09', 'rm')
    ctx['released'] = lst[0]['id']
    s, b = api.call('DELETE', f"/api/bookings/{ctx['released']}", 'rm')
    s2, bench = api.call('GET', '/api/employees?date=2026-10-06&bench=true', 'rm')
    return s in (200, 204) and 'Козлова Дарья' in names(bench), (
        f"код {s}, бронь №{ctx['released']} {lst[0]['title']} снята; Козлова Дарья в резерве на 06.10.2026")


def uc7_release_again(api, ctx):
    s, b = api.call('DELETE', f"/api/bookings/{ctx['released']}", 'rm')
    return s == 422 and b.get('code') == 'RELEASED', problem(s, b)


case('UC-7-01', 'Просмотр пула ресурсов и профиля сотрудника', ['FR7-1'], [
    ('Войти под учетной записью rm, открыть резерв на 05.10.2026: GET /api/employees?date=2026-10-05&bench=true',
     'Код 200; список свободных сотрудников (Bench), в том числе Ковалёв Алексей', uc7_pool),
    ('Открыть профиль сотрудника Ковалёв Алексей на 20.10.2026: GET /api/employees/1',
     'Код 200; загрузка 150 %, брони ATLAS и ORION', uc7_profile),
])
case('UC-7-02', 'Установка жесткой брони с датами аллокации', ['FR7-2', 'FR7-3'], [
    ('Создать жесткую бронь: POST /api/bookings, сотрудник Козлова Дарья, проект ORION, 12.10.2026 – 23.10.2026, 50 %',
     'Код 201; бронь HARD создана на указанные даты', uc7_hard),
    ('Открыть профиль сотрудника на 14.10.2026',
     'Загрузка сотрудника 50 %, сотрудник не в резерве', uc7_hard_effect),
])
case('UC-7-03', 'Регистрация ресурсного конфликта при перегрузке сотрудника', ['FR7-3'], [
    ('Создать жесткую бронь сотрудника Лис Сергей на ATLAS 12.10.2026 – 16.10.2026, 100 %',
     'Код 201; предупреждение о перегрузке с бронью HELIX, зарегистрирован конфликт с загрузкой 150 %', uc7_overload),
])
case('UC-7-04', 'Перевод мягкой брони в жесткую', ['FR7-2'], [
    ('Найти бронь ORION сотрудника Алиев Тимур и изменить ее: PATCH /api/bookings/{id}, вид HARD, загрузка 60 %',
     'Код 200; бронь стала жесткой, загрузка 60 %', uc7_harden),
])
case('UC-7-05', 'Запрет брони на период защищенного обучения', ['FR7-2', 'FR9-3'], [
    ('Создать мягкую бронь сотрудника Ковалёв Алексей на 16.11.2026 – 18.11.2026',
     'Код 422; сообщение о пересечении с защищенным обучением «Курс «Spring AI: основы»»', uc7_protected),
])
case('UC-7-06', 'Снятие брони и возврат сотрудника в пул ресурсов', ['FR7-4'], [
    ('Снять бронь ATLAS сотрудника Козлова Дарья: DELETE /api/bookings/{id}',
     'Код 200; бронь снята, сотрудник отображается в резерве на 06.10.2026', uc7_release),
    ('Повторно снять ту же бронь',
     'Код 422; сообщение «Бронь уже снята»', uc7_release_again),
])

# ---------- UC-8
group('UC-8', 'Просматривать персональный график загрузки')


def uc8_month(api, ctx):
    s, b = api.call('GET', '/api/me/calendar?period=MONTH&date=2026-10-15', 'emp')
    items = [f"{i['title']} {i['startDate']} – {i['endDate']}, {i['loadPercent']} %" for i in b['items']]
    ok = s == 200 and b['from'] == '2026-10-01' and b['to'] == '2026-10-31' and len(b['items']) == 2
    average = str(b['averageLoad']).replace('.', ',')
    return ok, (f"код {s}, {b['from']} – {b['to']}, {b['workdays']} раб. дней, средняя загрузка {average} %, "
                f"пиковая {b['peakLoad']} %; {'; '.join(items)}")


def uc8_week(api, ctx):
    s, b = api.call('GET', '/api/me/calendar?period=WEEK&date=2026-10-21', 'emp')
    return s == 200 and (b['from'], b['to']) == ('2026-10-19', '2026-10-25'), (
        f"неделя {b['from']} – {b['to']}, {b['workdays']} раб. дней, пиковая загрузка {b['peakLoad']} %")


def uc8_quarter(api, ctx):
    s, b = api.call('GET', '/api/me/calendar?period=QUARTER&date=2026-10-21', 'emp')
    kinds = sorted({i['type'] for i in b['items']})
    return s == 200 and (b['from'], b['to']) == ('2026-10-01', '2026-12-31') and 'TRAINING' in kinds, (
        f"квартал {b['from']} – {b['to']}, элементов {len(b['items'])}: {names(b['items'], 'title')}")


def uc8_bench(api, ctx):
    s, b = api.call('GET', '/api/me/calendar?period=WEEK&date=2026-10-07', 'emp2')
    return s == 200 and b['bench'] and not b['items'] and len(b['hints']) == 2, (
        f"код {s}, {b['employee']}: признак резерва {str(b['bench']).lower()}, ссылки: {', '.join(b['hints'])}")


def uc8_foreign(api, ctx):
    s, b = api.call('GET', '/api/employees/2/calendar', 'emp')
    return s == 403, problem(s, b)


def uc8_manager(api, ctx):
    s, b = api.call('GET', '/api/employees/2/calendar?period=MONTH&date=2026-10-15', 'rm')
    return s == 200, f"код {s}, календарь сотрудника {b['employee']} открыт ресурсным менеджером"


case('UC-8-01', 'Просмотр своего календаря за месяц', ['FR8-1', 'FR8-2'], [
    ('Войти под учетной записью emp, выполнить GET /api/me/calendar?period=MONTH&date=2026-10-15',
     'Код 200; период 01.10.2026 – 31.10.2026, 22 рабочих дня, проекты ATLAS и ORION с процентом загрузки, '
     'средняя и пиковая загрузка', uc8_month),
])
case('UC-8-02', 'Выбор периода отображения: неделя и квартал', ['FR8-1', 'FR8-2'], [
    ('Выбрать период «неделя» для даты 21.10.2026',
     'Отображается неделя 19.10.2026 – 25.10.2026', uc8_week),
    ('Выбрать период «квартал» для той же даты',
     'Отображается квартал 01.10.2026 – 31.12.2026, включая блок обучения', uc8_quarter),
])
case('UC-8-03', 'Отображение календаря сотрудника в резерве', ['FR8-3'], [
    ('Войти под учетной записью emp2, открыть календарь на неделю 05.10.2026 – 11.10.2026',
     'Календарь пуст, установлен признак резерва (Bench), выведены ссылки на образовательные порталы', uc8_bench),
])
case('UC-8-04', 'Разграничение доступа к календарям сотрудников', ['FR8-1'], [
    ('Под учетной записью emp открыть календарь другого сотрудника: GET /api/employees/2/calendar',
     'Код 403; доступ запрещен', uc8_foreign),
    ('Под учетной записью rm открыть тот же календарь',
     'Код 200; календарь доступен менеджеру', uc8_manager),
])

# ---------- UC-9
group('UC-9', 'Резервировать время на обучение')


def uc9_courses(api, ctx):
    s, b = api.call('GET', '/api/courses', 'ld')
    return s == 200 and len(b) >= 3, f"код {s}, курсов в каталоге: {len(b)} ({names(b, 'title')})"


def uc9_reserve(api, ctx):
    s, b = api.call('POST', '/api/trainings', 'ld', {
        'employeeId': 8, 'courseTitle': 'ISTQB Advanced Test Automation Engineer', 'provider': 'ISTQB',
        'startDate': '2026-11-09', 'endDate': '2026-11-13', 'loadPercent': 100, 'protectedTime': True})
    bk = b.get('booking', {})
    return s == 201 and bk['type'] == 'TRAINING' and bk['protectedTime'], (
        f"код {s}, блок №{bk.get('id')} «{bk.get('title')}», {bk.get('startDate')} – {bk.get('endDate')}, "
        f"защищенное время: {str(bk.get('protectedTime')).lower()}")


def uc9_calendar(api, ctx):
    s, b = api.call('GET', '/api/employees/8/calendar?period=MONTH&date=2026-11-10', 'ld')
    item = next((i for i in b['items'] if i['type'] == 'TRAINING'), None)
    return item is not None, f"в календаре {b['employee']} за ноябрь: {names(b['items'], 'title')}"


def candidate_names(api):
    return [c['name'] for c in api.call('GET', '/api/requests/2/candidates', 'rm')[1]]


def uc9_before(api, ctx):
    found = candidate_names(api)
    return 'Юсупова Карина' in found, f"кандидаты по запросу №2: {', '.join(found)}"


def uc9_reserve_yusupova(api, ctx):
    s, b = api.call('POST', '/api/trainings', 'ld', {
        'employeeId': 9, 'courseTitle': 'Playwright для автоматизаторов', 'provider': 'EPAM University',
        'startDate': '2026-11-16', 'endDate': '2026-11-20', 'loadPercent': 100, 'protectedTime': True})
    ctx['training'] = b.get('booking', {}).get('id')
    return s == 201, f"код {s}, блок №{ctx['training']} 16.11.2026 – 20.11.2026"


def uc9_after(api, ctx):
    found = candidate_names(api)
    return 'Юсупова Карина' not in found, f"кандидаты по запросу №2: {', '.join(found)}"


def uc9_conflict(api, ctx):
    s, b = api.call('POST', '/api/trainings', 'ld', {
        'employeeId': 1, 'courseTitle': 'Kubernetes для разработчиков', 'startDate': '2026-10-20',
        'endDate': '2026-10-22', 'loadPercent': 100, 'protectedTime': True})
    return s == 422 and b.get('code') == 'PROTECTED_TIME' and 'выберите другие даты' in b['detail'], problem(s, b)


def uc9_conflict_effect(api, ctx):
    s, b = api.call('GET', '/api/employees/1/calendar?period=MONTH&date=2026-10-20', 'ld')
    return all(i['type'] != 'TRAINING' for i in b['items']), f"в календаре за октябрь: {names(b['items'], 'title')}"


def uc9_cancel(api, ctx):
    s, b = api.call('DELETE', f"/api/trainings/{ctx['training']}", 'ld')
    found = candidate_names(api)
    return s in (200, 204) and 'Юсупова Карина' in found, f"код {s}; кандидаты по запросу №2: {', '.join(found)}"


case('UC-9-01', 'Резервирование защищенного блока обучения', ['FR9-1', 'FR9-2'], [
    ('Войти под учетной записью ld, открыть каталог курсов: GET /api/courses',
     'Код 200; список курсов', uc9_courses),
    ('Добавить блок обучения: POST /api/trainings, сотрудник Лис Сергей, курс «ISTQB Advanced Test Automation '
     'Engineer», 09.11.2026 – 13.11.2026, 100 %, флаг «Запрет на проектное бронирование»',
     'Код 201; создан блок обучения с защищенным временем', uc9_reserve),
    ('Открыть календарь сотрудника за ноябрь 2026 г.',
     'Блок обучения отображается в календаре', uc9_calendar),
])
case('UC-9-02', 'Исключение сотрудника из подбора на период обучения', ['FR9-3'], [
    ('Под учетной записью rm открыть кандидатов по запросу №2 (11.11.2026 – 17.11.2026)',
     'Юсупова Карина присутствует в выборке', uc9_before),
    ('Под учетной записью ld зарезервировать защищенное обучение Юсуповой Карины на 16.11.2026 – 20.11.2026',
     'Код 201; блок обучения создан', uc9_reserve_yusupova),
    ('Под учетной записью rm повторно открыть кандидатов по запросу №2',
     'Юсупова Карина исключена из выборки', uc9_after),
])
case('UC-9-03', 'Предупреждение о пересечении обучения с жесткой бронью', ['FR9-4'], [
    ('Зарезервировать защищенное обучение сотрудника Ковалёв Алексей на 20.10.2026 – 22.10.2026',
     'Код 422; сообщение о пересечении с жесткой бронью и требование выбрать другие даты', uc9_conflict),
    ('Открыть календарь сотрудника за октябрь 2026 г.',
     'Блок обучения не добавлен', uc9_conflict_effect),
])
case('UC-9-04', 'Отмена блока обучения', ['FR9-1', 'FR9-3'], [
    ('Отменить обучение Юсуповой Карины из UC-9-02: DELETE /api/trainings/{id}',
     'Код 200; блок снят, сотрудник снова доступен в выборке кандидатов по запросу №2', uc9_cancel),
])


# ---------------------------------------------------------------- выполнение


def run():
    started = datetime.datetime.now()
    total = passed = 0
    for g in GROUPS:
        print(f"== {g['uc']} {g['title']}: запуск приложения")
        with Server() as api:
            ctx = {}
            for c in g['cases']:
                c['results'] = []
                for action, expected, check in c['steps']:
                    try:
                        ok, actual = check(api, ctx)
                    except Exception as e:  # тест-кейс не завершен
                        ok, actual = False, f'ошибка выполнения: {e!r}'
                    c['results'].append({'action': action, 'expected': expected, 'actual': actual, 'passed': bool(ok)})
                for r in c['results']:
                    r['actual'] = ISO_DATE.sub(lambda m: f'{m[3]}.{m[2]}.{m[1]}', r['actual'])
                c['status'] = 'passed' if all(r['passed'] for r in c['results']) else 'failed'
                total += 1
                passed += c['status'] == 'passed'
                print(f"   {c['id']:8} {c['status']:6} {c['title']}")
                for r in c['results']:
                    if not r['passed']:
                        print('      ', r['action'], '->', r['actual'])
                del c['steps']
    report = {'date': started.strftime('%Y-%m-%d'), 'started': started.isoformat(timespec='seconds'),
              'database': DB_URL, 'total': total, 'passed': passed, 'groups': GROUPS}
    OUT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'Пройдено {passed} из {total}; результаты: {OUT}')
    return passed == total


if __name__ == '__main__':
    sys.exit(0 if run() else 1)
