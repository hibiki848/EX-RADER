DELETE FROM notifications WHERE type = 'CONSULTATION_ANSWER';
DELETE FROM reports WHERE target_type IN ('CONSULTATION', 'CONSULTATION_ANSWER');
DROP TABLE consultation_answers;
DROP TABLE consultation_tags;
DROP TABLE consultations;
