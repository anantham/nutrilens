-- V14: Add validation failures table for tracking AI sanity check failures
-- This allows us to track when AI generates impossible/invalid nutrition data

CREATE TABLE validation_failures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meal_id UUID NOT NULL REFERENCES meals(id) ON DELETE CASCADE,

    -- Issue counts
    issue_count INTEGER NOT NULL,
    error_count INTEGER NOT NULL,
    warning_count INTEGER NOT NULL,

    -- Detailed issues as JSON
    issues JSONB NOT NULL,

    -- Context at time of failure
    confidence_score DECIMAL(3,2),
    raw_ai_response TEXT,
    meal_description TEXT,

    -- Timestamp
    failed_at TIMESTAMP DEFAULT NOW(),

    -- Constraints
    CONSTRAINT fk_meal FOREIGN KEY (meal_id) REFERENCES meals(id) ON DELETE CASCADE
);

-- Indexes for analytics queries
CREATE INDEX idx_validation_meal ON validation_failures(meal_id);
CREATE INDEX idx_validation_error_count ON validation_failures(error_count);
CREATE INDEX idx_validation_failed_at ON validation_failures(failed_at);

-- Comments for documentation
COMMENT ON TABLE validation_failures IS 'Tracks AI validation failures when impossible/invalid nutrition data is generated';
COMMENT ON COLUMN validation_failures.issue_count IS 'Total number of validation issues (errors + warnings)';
COMMENT ON COLUMN validation_failures.error_count IS 'Number of ERROR-level issues (critical failures)';
COMMENT ON COLUMN validation_failures.warning_count IS 'Number of WARNING-level issues (suspicious but not impossible)';
COMMENT ON COLUMN validation_failures.issues IS 'Full list of validation issues as JSON array';
COMMENT ON COLUMN validation_failures.confidence_score IS 'AI confidence score when it failed (0-1)';
COMMENT ON COLUMN validation_failures.raw_ai_response IS 'Raw AI response that failed validation (for debugging)';
