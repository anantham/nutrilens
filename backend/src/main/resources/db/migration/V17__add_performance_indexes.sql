-- Migration V17: Add Performance Optimization Indexes
-- Purpose: Optimize query performance for frequently-used query patterns
-- Author: Claude Code - Performance Optimization Sprint
-- Date: 2025-11-10

-- ============================================
-- MEALS TABLE - Additional Composite Indexes
-- ============================================

-- Composite index for filtering by user and meal type, ordered by time
-- Supports query: findByUserIdAndMealTypeOrderByMealTimeDesc
CREATE INDEX IF NOT EXISTS idx_meals_user_type_time
    ON meals(user_id, meal_type, meal_time DESC);

-- Composite index for date range queries (already partially covered, but this is more specific)
-- Supports query: findByUserIdAndMealTimeBetweenOrderByMealTimeDesc
-- Note: idx_meals_user_meal_time (user_id, meal_time DESC) from V1 already covers this well
-- But we can add INCLUDE columns for covering index (PostgreSQL 11+)
CREATE INDEX IF NOT EXISTS idx_meals_user_time_covering
    ON meals(user_id, meal_time DESC)
    INCLUDE (meal_type, calories, protein_g, fat_g, carbohydrates_g, confidence);

-- ============================================
-- AI CORRECTION LOGS - Additional Indexes
-- ============================================

-- Composite index for time-based queries with field filtering
-- Supports queries that filter by corrected_at and group by field_name
CREATE INDEX IF NOT EXISTS idx_ai_correction_time_field
    ON ai_correction_logs(corrected_at, field_name);

-- Partial index for high-confidence predictions (for confidence calibration queries)
-- Only indexes rows where confidence >= 0.8
CREATE INDEX IF NOT EXISTS idx_ai_correction_high_confidence
    ON ai_correction_logs(field_name, confidence_score, percent_error)
    WHERE confidence_score >= 0.8;

-- Partial index for low-confidence predictions
CREATE INDEX IF NOT EXISTS idx_ai_correction_low_confidence
    ON ai_correction_logs(field_name, confidence_score, percent_error)
    WHERE confidence_score < 0.5;

-- Composite index for weekly accuracy trend analysis
-- Supports DATE_TRUNC queries
CREATE INDEX IF NOT EXISTS idx_ai_correction_date_trunc
    ON ai_correction_logs(DATE_TRUNC('week', corrected_at), field_name, percent_error);

-- ============================================
-- USERS TABLE - Email Lookup Optimization
-- ============================================

-- Email is already UNIQUE, which creates an implicit index
-- But we can add a covering index for authentication queries
-- This avoids table lookups when checking email existence
CREATE INDEX IF NOT EXISTS idx_users_email_covering
    ON users(email)
    INCLUDE (password_hash, role, id);

-- ============================================
-- PERFORMANCE COMMENTS
-- ============================================

COMMENT ON INDEX idx_meals_user_type_time IS 'Optimizes queries filtering meals by user and type, ordered by time';
COMMENT ON INDEX idx_meals_user_time_covering IS 'Covering index to avoid table lookups for meal list queries';
COMMENT ON INDEX idx_ai_correction_time_field IS 'Optimizes temporal analysis queries grouped by field';
COMMENT ON INDEX idx_ai_correction_high_confidence IS 'Partial index for analyzing high-confidence AI predictions (>= 0.8)';
COMMENT ON INDEX idx_ai_correction_low_confidence IS 'Partial index for analyzing low-confidence AI predictions (< 0.5)';
COMMENT ON INDEX idx_ai_correction_date_trunc IS 'Optimizes weekly accuracy trend queries with DATE_TRUNC';
COMMENT ON INDEX idx_users_email_covering IS 'Covering index for authentication queries (avoids table lookup)';

-- ============================================
-- ANALYZE TABLES
-- ============================================

-- Update table statistics for query planner
ANALYZE meals;
ANALYZE ai_correction_logs;
ANALYZE users;
