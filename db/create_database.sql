-- Создание базы данных ResPlan для REST API (ЛР 9).
-- Выполняется администратором СУБД в системной базе postgres (вне транзакции).
CREATE DATABASE resplan_lab9 WITH ENCODING = 'UTF8' TEMPLATE = template0;
COMMENT ON DATABASE resplan_lab9 IS 'ResPlan: REST API (ЛР 9)';

-- Учетная запись приложения (требуется атрибут CREATEROLE); таблицы создает Hibernate (ddl-auto: create)
-- CREATE ROLE resplan_app WITH LOGIN PASSWORD '<пароль>';
-- ALTER DATABASE resplan_lab9 OWNER TO resplan_app;
