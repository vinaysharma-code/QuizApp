ALTER TABLE quizzes ADD COLUMN owner_id varchar(36) REFERENCES app_users(id);
ALTER TABLE quizzes ADD COLUMN opens_at timestamptz;
ALTER TABLE quizzes ADD COLUMN closes_at timestamptz;
ALTER TABLE quizzes ADD COLUMN max_attempts integer NOT NULL DEFAULT 3 CHECK(max_attempts BETWEEN 1 AND 100);
ALTER TABLE quizzes ADD COLUMN passing_percent integer NOT NULL DEFAULT 50 CHECK(passing_percent BETWEEN 0 AND 100);
ALTER TABLE quizzes ADD COLUMN result_release_policy varchar(20) NOT NULL DEFAULT 'IMMEDIATE' CHECK(result_release_policy IN ('IMMEDIATE','AFTER_CLOSE','AT_TIME','MANUAL'));
ALTER TABLE quizzes ADD COLUMN results_release_at timestamptz;
ALTER TABLE quizzes ADD COLUMN results_released boolean NOT NULL DEFAULT false;
ALTER TABLE quizzes ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE quizzes ADD CONSTRAINT valid_quiz_window CHECK(opens_at IS NULL OR closes_at IS NULL OR opens_at<closes_at);
CREATE INDEX idx_quiz_owner ON quizzes(owner_id);
ALTER TABLE quiz_attempts ADD COLUMN attempt_number integer;
WITH numbered AS (
 SELECT id,row_number() OVER (PARTITION BY quiz_id,student_id ORDER BY started_at,id) AS n FROM quiz_attempts
) UPDATE quiz_attempts a SET attempt_number=n.n FROM numbered n WHERE a.id=n.id;
ALTER TABLE quiz_attempts ALTER COLUMN attempt_number SET NOT NULL;
ALTER TABLE quiz_attempts ADD CONSTRAINT unique_quiz_student_attempt UNIQUE(quiz_id,student_id,attempt_number);
ALTER TABLE quiz_attempts ADD CONSTRAINT positive_attempt_number CHECK(attempt_number>0);
CREATE INDEX idx_attempt_quiz_started ON quiz_attempts(quiz_id,started_at);
ALTER TABLE quizzes ADD COLUMN question_count integer NOT NULL DEFAULT 0;
UPDATE quizzes q SET question_count=(SELECT count(*) FROM quiz_questions qq WHERE qq.quiz_id=q.id);
