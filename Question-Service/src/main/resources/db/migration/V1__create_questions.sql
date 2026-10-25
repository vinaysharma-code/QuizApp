CREATE TABLE questions (
    id varchar(36) PRIMARY KEY,
    question_title varchar(1000) NOT NULL,
    option_a varchar(1000) NOT NULL,
    option_b varchar(1000) NOT NULL,
    option_c varchar(1000) NOT NULL,
    option_d varchar(1000) NOT NULL,
    right_answer varchar(1000) NOT NULL,
    difficulty_level varchar(100),
    category varchar(100) NOT NULL,
    CONSTRAINT uniq_category_title UNIQUE (category, question_title)
);
CREATE INDEX idx_question_category ON questions(category);
