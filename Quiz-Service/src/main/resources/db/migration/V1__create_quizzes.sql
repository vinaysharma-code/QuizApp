CREATE TABLE quizzes (
    id varchar(36) PRIMARY KEY,
    title varchar(100) NOT NULL
);
CREATE TABLE quiz_questions (
    quiz_id varchar(36) NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    question_order integer NOT NULL,
    question_id varchar(36) NOT NULL,
    PRIMARY KEY (quiz_id, question_order),
    CONSTRAINT uniq_quiz_question UNIQUE (quiz_id, question_id)
);
