-- 네이티브 Postgres ENUM 대신 VARCHAR + CHECK 제약을 사용한다.
-- Hibernate가 enum 파라미터를 바인딩할 때 별도 캐스팅 설정 없이는 네이티브 ENUM 컬럼에 값을 못 넣는 문제를 피하기 위함.
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    student_number  VARCHAR(50) NOT NULL UNIQUE,
    role            VARCHAR(20) NOT NULL DEFAULT 'STUDENT' CHECK (role IN ('STUDENT', 'ADMIN')),
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE courses (
    id                      BIGSERIAL PRIMARY KEY,
    course_code             VARCHAR(50) NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    professor_name          VARCHAR(100) NOT NULL,
    department              VARCHAR(100) NOT NULL,
    credit                  INT NOT NULL,
    capacity                INT NOT NULL,
    current_enrolled_count  INT NOT NULL DEFAULT 0,
    semester                VARCHAR(20) NOT NULL,
    enrollment_start_at     TIMESTAMP NOT NULL,
    enrollment_end_at       TIMESTAMP NOT NULL,
    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_courses_semester_department ON courses (semester, department);

CREATE TABLE enrollments (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users (id),
    course_id       BIGINT NOT NULL REFERENCES courses (id),
    status          VARCHAR(20) NOT NULL DEFAULT 'ENROLLED' CHECK (status IN ('ENROLLED', 'CANCELLED')),
    enrolled_at     TIMESTAMP NOT NULL DEFAULT now(),
    cancelled_at    TIMESTAMP
);

-- 재신청 미허용: 취소 후에도 (user_id, course_id) 조합은 재사용하지 않는다
CREATE UNIQUE INDEX uq_enrollments_user_course ON enrollments (user_id, course_id);
CREATE INDEX idx_enrollments_user_id ON enrollments (user_id);
CREATE INDEX idx_enrollments_course_id_status ON enrollments (course_id, status);

CREATE TABLE waiting_queue (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users (id),
    course_id       BIGINT NOT NULL REFERENCES courses (id),
    status          VARCHAR(20) NOT NULL DEFAULT 'WAITING'
                        CHECK (status IN ('WAITING', 'NOTIFIED', 'CONFIRMED', 'EXPIRED', 'CANCELLED')),
    entered_at      TIMESTAMP NOT NULL DEFAULT now(),
    notified_at     TIMESTAMP,
    expires_at      TIMESTAMP
);

CREATE INDEX idx_waiting_queue_course_entered ON waiting_queue (course_id, entered_at);
CREATE INDEX idx_waiting_queue_user_course ON waiting_queue (user_id, course_id);

CREATE TABLE bot_detection_logs (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users (id),
    course_id           BIGINT REFERENCES courses (id),
    request_features    JSONB NOT NULL,
    suspicion_score     FLOAT NOT NULL,
    action_taken        VARCHAR(50) NOT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT now()
);
