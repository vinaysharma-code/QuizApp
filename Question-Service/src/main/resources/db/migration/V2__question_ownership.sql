ALTER TABLE questions ADD COLUMN owner_email varchar(254);
ALTER TABLE questions DROP CONSTRAINT uniq_category_title;
ALTER TABLE questions ADD CONSTRAINT uniq_owner_category_title UNIQUE(owner_email,category,question_title);
CREATE INDEX idx_question_owner ON questions(owner_email);
