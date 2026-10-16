-- GrowthLoop MySQL schema (generated from the JPA entities - keep in sync).
-- The backend also creates/updates these tables automatically (spring.jpa.hibernate.ddl-auto=update).
CREATE DATABASE IF NOT EXISTS growthloop CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE growthloop;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  email VARCHAR(150) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  full_name VARCHAR(120) NOT NULL,
  role VARCHAR(30) NOT NULL,
  enabled BIT(1) NOT NULL,
  created_at DATETIME(6),
  updated_at DATETIME(6),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS career_goals (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL UNIQUE,
  description TEXT,
  created_at DATETIME(6),
  updated_at DATETIME(6),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS skills (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL UNIQUE,
  category VARCHAR(60),
  description TEXT,
  difficulty VARCHAR(30),
  estimated_hours DOUBLE,
  practice_task TEXT,
  created_at DATETIME(6),
  updated_at DATETIME(6),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS teacher_profiles (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL UNIQUE,
  department VARCHAR(120),
  designation VARCHAR(120),
  expertise TEXT,
  availability VARCHAR(255),
  is_mentor BIT(1),
  trust_score DOUBLE,
  created_at DATETIME(6),
  updated_at DATETIME(6),
  PRIMARY KEY (id),
  CONSTRAINT fk_teacher_profiles_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS student_profiles (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL UNIQUE,
  roll_number VARCHAR(30) UNIQUE,
  phone VARCHAR(20),
  education VARCHAR(120),
  branch VARCHAR(120),
  semester INT,
  career_goal_id BIGINT,
  interests TEXT,
  learning_preferences VARCHAR(255),
  availability VARCHAR(255),
  bio TEXT,
  assigned_teacher_id BIGINT,
  progress_percent DOUBLE,
  analysis_stale BIT(1),
  last_analyzed_at DATETIME(6),
  created_at DATETIME(6),
  updated_at DATETIME(6),
  PRIMARY KEY (id),
  CONSTRAINT fk_student_profiles_user_id FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_student_profiles_career_goal_id FOREIGN KEY (career_goal_id) REFERENCES career_goals (id),
  CONSTRAINT fk_student_profiles_assigned_teacher_id FOREIGN KEY (assigned_teacher_id) REFERENCES teacher_profiles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS skill_prerequisites (
  id BIGINT NOT NULL AUTO_INCREMENT,
  skill_id BIGINT NOT NULL,
  prerequisite_id BIGINT NOT NULL,
  created_at DATETIME(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_skill_prerequisites_skill_id_prerequisite_id (skill_id, prerequisite_id),
  CONSTRAINT fk_skill_prerequisites_skill_id FOREIGN KEY (skill_id) REFERENCES skills (id),
  CONSTRAINT fk_skill_prerequisites_prerequisite_id FOREIGN KEY (prerequisite_id) REFERENCES skills (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS student_skills (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  skill_id BIGINT NOT NULL,
  proficiency VARCHAR(30) NOT NULL,
  created_at DATETIME(6),
  updated_at DATETIME(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_student_skills_student_id_skill_id (student_id, skill_id),
  CONSTRAINT fk_student_skills_student_id FOREIGN KEY (student_id) REFERENCES student_profiles (id),
  CONSTRAINT fk_student_skills_skill_id FOREIGN KEY (skill_id) REFERENCES skills (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS career_goal_skills (
  id BIGINT NOT NULL AUTO_INCREMENT,
  career_goal_id BIGINT NOT NULL,
  skill_id BIGINT NOT NULL,
  importance INT NOT NULL,
  required_level VARCHAR(30) NOT NULL,
  created_at DATETIME(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_career_goal_skills_career_goal_id_skill_id (career_goal_id, skill_id),
  CONSTRAINT fk_career_goal_skills_career_goal_id FOREIGN KEY (career_goal_id) REFERENCES career_goals (id),
  CONSTRAINT fk_career_goal_skills_skill_id FOREIGN KEY (skill_id) REFERENCES skills (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS academic_records (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  subject VARCHAR(120) NOT NULL,
  marks DOUBLE NOT NULL,
  max_marks DOUBLE NOT NULL,
  percentage DOUBLE NOT NULL,
  semester INT,
  academic_year VARCHAR(20),
  created_at DATETIME(6),
  updated_at DATETIME(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_academic_records_student_id_subject_semester_academic_year (student_id, subject, semester, academic_year),
  CONSTRAINT fk_academic_records_student_id FOREIGN KEY (student_id) REFERENCES student_profiles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS attendance (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  subject VARCHAR(120) NOT NULL,
  semester INT,
  academic_year VARCHAR(20),
  attended_classes INT,
  total_classes INT,
  percentage DOUBLE NOT NULL,
  created_at DATETIME(6),
  updated_at DATETIME(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_attendance_student_id_subject_semester_academic_year (student_id, subject, semester, academic_year),
  CONSTRAINT fk_attendance_student_id FOREIGN KEY (student_id) REFERENCES student_profiles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS assignments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  subject VARCHAR(120) NOT NULL,
  title VARCHAR(200) NOT NULL,
  status VARCHAR(30) NOT NULL,
  score DOUBLE,
  max_score DOUBLE,
  due_date DATE,
  semester INT,
  academic_year VARCHAR(20),
  created_at DATETIME(6),
  updated_at DATETIME(6),
  PRIMARY KEY (id),
  CONSTRAINT fk_assignments_student_id FOREIGN KEY (student_id) REFERENCES student_profiles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS risk_assessments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  risk_level VARCHAR(30) NOT NULL,
  probability DOUBLE,
  indicators_json TEXT,
  actions_json TEXT,
  features_json TEXT,
  model_version VARCHAR(80),
  created_at DATETIME(6),
  PRIMARY KEY (id),
  KEY idx_risk_assessments_student_id_created_at (student_id, created_at),
  CONSTRAINT fk_risk_assessments_student_id FOREIGN KEY (student_id) REFERENCES student_profiles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS skill_gap_results (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  career_goal_id BIGINT,
  coverage_percent DOUBLE,
  existing_json TEXT,
  partial_json TEXT,
  missing_json TEXT,
  next_skills_json TEXT,
  created_at DATETIME(6),
  PRIMARY KEY (id),
  KEY idx_skill_gap_results_student_id_created_at (student_id, created_at),
  CONSTRAINT fk_skill_gap_results_student_id FOREIGN KEY (student_id) REFERENCES student_profiles (id),
  CONSTRAINT fk_skill_gap_results_career_goal_id FOREIGN KEY (career_goal_id) REFERENCES career_goals (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS peer_matches (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  matched_user_id BIGINT NOT NULL,
  match_type VARCHAR(30) NOT NULL,
  score DOUBLE NOT NULL,
  reasons_json TEXT,
  can_help_with_json TEXT,
  created_at DATETIME(6),
  PRIMARY KEY (id),
  KEY idx_peer_matches_student_id (student_id),
  CONSTRAINT fk_peer_matches_student_id FOREIGN KEY (student_id) REFERENCES student_profiles (id),
  CONSTRAINT fk_peer_matches_matched_user_id FOREIGN KEY (matched_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS peer_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT,
  requester_id BIGINT NOT NULL,
  mentor_id BIGINT NOT NULL,
  skill_id BIGINT,
  topic VARCHAR(200) NOT NULL,
  session_date DATE,
  session_time TIME,
  status VARCHAR(30) NOT NULL,
  notes TEXT,
  created_at DATETIME(6),
  updated_at DATETIME(6),
  PRIMARY KEY (id),
  KEY idx_peer_sessions_status (status),
  CONSTRAINT fk_peer_sessions_requester_id FOREIGN KEY (requester_id) REFERENCES users (id),
  CONSTRAINT fk_peer_sessions_mentor_id FOREIGN KEY (mentor_id) REFERENCES users (id),
  CONSTRAINT fk_peer_sessions_skill_id FOREIGN KEY (skill_id) REFERENCES skills (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS feedback (
  id BIGINT NOT NULL AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  from_user_id BIGINT NOT NULL,
  to_user_id BIGINT NOT NULL,
  rating INT NOT NULL,
  helpfulness INT,
  comment TEXT,
  topics_covered VARCHAR(255),
  progress_update TEXT,
  created_at DATETIME(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_feedback_session_id_from_user_id (session_id, from_user_id),
  CONSTRAINT fk_feedback_session_id FOREIGN KEY (session_id) REFERENCES peer_sessions (id),
  CONSTRAINT fk_feedback_from_user_id FOREIGN KEY (from_user_id) REFERENCES users (id),
  CONSTRAINT fk_feedback_to_user_id FOREIGN KEY (to_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS recommendations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  type VARCHAR(30) NOT NULL,
  title VARCHAR(200) NOT NULL,
  description TEXT,
  priority INT,
  payload_json TEXT,
  active BIT(1) NOT NULL,
  created_at DATETIME(6),
  PRIMARY KEY (id),
  KEY idx_recommendations_student_id_active (student_id, active),
  CONSTRAINT fk_recommendations_student_id FOREIGN KEY (student_id) REFERENCES student_profiles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS learning_roadmaps (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  career_goal_id BIGINT,
  version INT NOT NULL,
  active BIT(1) NOT NULL,
  next_action TEXT,
  created_at DATETIME(6),
  PRIMARY KEY (id),
  KEY idx_learning_roadmaps_student_id_active (student_id, active),
  CONSTRAINT fk_learning_roadmaps_student_id FOREIGN KEY (student_id) REFERENCES student_profiles (id),
  CONSTRAINT fk_learning_roadmaps_career_goal_id FOREIGN KEY (career_goal_id) REFERENCES career_goals (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS roadmap_items (
  id BIGINT NOT NULL AUTO_INCREMENT,
  roadmap_id BIGINT NOT NULL,
  skill_id BIGINT NOT NULL,
  item_order INT NOT NULL,
  priority VARCHAR(20),
  prerequisites VARCHAR(255),
  status VARCHAR(30) NOT NULL,
  ready_now BIT(1),
  practice_task TEXT,
  estimated_hours DOUBLE,
  resource_ids VARCHAR(255),
  reason VARCHAR(255),
  completed_at DATETIME(6),
  created_at DATETIME(6),
  updated_at DATETIME(6),
  PRIMARY KEY (id),
  CONSTRAINT fk_roadmap_items_roadmap_id FOREIGN KEY (roadmap_id) REFERENCES learning_roadmaps (id),
  CONSTRAINT fk_roadmap_items_skill_id FOREIGN KEY (skill_id) REFERENCES skills (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS learning_resources (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  description TEXT,
  skill_id BIGINT,
  type VARCHAR(30) NOT NULL,
  url VARCHAR(500),
  difficulty VARCHAR(30),
  estimated_minutes INT,
  created_at DATETIME(6),
  updated_at DATETIME(6),
  PRIMARY KEY (id),
  CONSTRAINT fk_learning_resources_skill_id FOREIGN KEY (skill_id) REFERENCES skills (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL,
  message TEXT,
  is_read BIT(1) NOT NULL,
  created_at DATETIME(6),
  PRIMARY KEY (id),
  KEY idx_notifications_user_id_is_read (user_id, is_read),
  CONSTRAINT fk_notifications_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS = 1;
