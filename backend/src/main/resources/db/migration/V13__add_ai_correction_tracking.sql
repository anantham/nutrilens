-- V13: Add AI correction tracking for telemetry and accuracy measurement
-- This allows us to track when users correct AI values and measure accuracy over time

-- Add user_edited flag to meals table
ALTER TABLE meals
    ADD COLUMN user_edited BOOLEAN DEFAULT FALSE,
    ADD COLUMN raw_ai_response TEXT;

COMMENT ON COLUMN meals.user_edited IS 'True if user manually edited any AI-generated values';
COMMENT ON COLUMN meals.raw_ai_response IS 'Raw JSON response from AI for debugging hallucinations';

-- Create ai_correction_logs table
CREATE TABLE ai_correction_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meal_id UUID NOT NULL REFERENCES meals(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- What field was corrected
    field_name VARCHAR(50) NOT NULL,

    -- Values
    ai_value DECIMAL(10,2),
    user_value DECIMAL(10,2),
    percent_error DECIMAL(10,2), -- (user_value - ai_value) / user_value * 100

    -- Context at time of correction
    confidence_score DECIMAL(3,2),
    location_type VARCHAR(50), -- restaurant/home/cafe/etc
    location_is_restaurant BOOLEAN,
    location_is_home BOOLEAN,
    meal_description TEXT,
    meal_time TIMESTAMP,

    -- Timestamps
    ai_analyzed_at TIMESTAMP,
    corrected_at TIMESTAMP DEFAULT NOW(),

    -- Indexes for analytics queries
    CONSTRAINT fk_meal FOREIGN KEY (meal_id) REFERENCES meals(id) ON DELETE CASCADE,
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Indexes for fast analytics queries
CREATE INDEX idx_correction_field_name ON ai_correction_logs(field_name);
CREATE INDEX idx_correction_user ON ai_correction_logs(user_id);
CREATE INDEX idx_correction_meal ON ai_correction_logs(meal_id);
CREATE INDEX idx_correction_location_type ON ai_correction_logs(location_type);
CREATE INDEX idx_correction_confidence ON ai_correction_logs(confidence_score);
CREATE INDEX idx_correction_date ON ai_correction_logs(corrected_at);

-- Comments for documentation
COMMENT ON TABLE ai_correction_logs IS 'Tracks user corrections to AI-generated nutrition values for accuracy measurement';
COMMENT ON COLUMN ai_correction_logs.field_name IS 'Name of the field corrected (calories, protein_g, fat_g, etc.)';
COMMENT ON COLUMN ai_correction_logs.ai_value IS 'Original value from AI analysis';
COMMENT ON COLUMN ai_correction_logs.user_value IS 'User-corrected value';
COMMENT ON COLUMN ai_correction_logs.percent_error IS 'Percentage error: (user_value - ai_value) / user_value * 100';
COMMENT ON COLUMN ai_correction_logs.confidence_score IS 'AI confidence score (0-1) at time of analysis';
COMMENT ON COLUMN ai_correction_logs.location_type IS 'Type of location where meal was eaten';
COMMENT ON COLUMN ai_correction_logs.meal_description IS 'User description of the meal for pattern analysis';
