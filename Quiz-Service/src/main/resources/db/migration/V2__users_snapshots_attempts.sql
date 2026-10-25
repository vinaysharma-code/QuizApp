CREATE TABLE app_users (
 id varchar(36) PRIMARY KEY,email varchar(254) NOT NULL UNIQUE,user_name varchar(100) NOT NULL,
 password varchar(100) NOT NULL,role varchar(20) NOT NULL CHECK(role IN ('ADMIN','STUDENT')),active boolean NOT NULL DEFAULT true
);
ALTER TABLE quizzes ADD COLUMN duration_seconds integer NOT NULL DEFAULT 600 CHECK(duration_seconds BETWEEN 30 AND 7200);
ALTER TABLE quizzes ADD COLUMN published boolean NOT NULL DEFAULT false;
ALTER TABLE quizzes ADD COLUMN published_at timestamptz;
CREATE TABLE quiz_snapshot_questions (
 quiz_id varchar(36) NOT NULL REFERENCES quizzes(id),question_order integer NOT NULL,
 question_id varchar(36) NOT NULL,question_title varchar(1000) NOT NULL,
 option_a varchar(1000) NOT NULL,option_b varchar(1000) NOT NULL,option_c varchar(1000) NOT NULL,option_d varchar(1000) NOT NULL,
 right_answer varchar(1000) NOT NULL,PRIMARY KEY(quiz_id,question_order),UNIQUE(quiz_id,question_id)
);
CREATE TABLE quiz_attempts (
 id varchar(36) PRIMARY KEY,quiz_id varchar(36) NOT NULL REFERENCES quizzes(id),student_id varchar(36) NOT NULL REFERENCES app_users(id),
 started_at timestamptz NOT NULL,deadline timestamptz NOT NULL,submitted_at timestamptz,score integer,
 status varchar(20) NOT NULL CHECK(status IN ('IN_PROGRESS','SUBMITTED','EXPIRED')),version bigint NOT NULL DEFAULT 0,
 CHECK(deadline>started_at),CHECK(score IS NULL OR score>=0)
);
CREATE INDEX idx_attempt_student ON quiz_attempts(student_id);
CREATE TABLE attempt_responses (
 attempt_id varchar(36) NOT NULL REFERENCES quiz_attempts(id) ON DELETE CASCADE,
 question_id varchar(36) NOT NULL,answer varchar(1000) NOT NULL,PRIMARY KEY(attempt_id,question_id)
);
