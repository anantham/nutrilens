-- Migration V16: Add AI Correction Tracking
-- Purpose: Track user corrections to AI-generated nutrition data to measure accuracy and detect systematic biases
-- Author: Claude Code
-- Date: 2025-11-10

CREATE TABLE ai_correction_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meal_id UUID NOT NULL REFERENCES meals(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- What field was corrected
    field_name VARCHAR(50) NOT NULL, -- 'calories', 'protein_g', 'fat_g', 'carbohydrates_g', etc.

    -- Values
    ai_value DECIMAL(10,2), -- What AI originally predicted
    user_value DECIMAL(10,2), -- What user corrected it to
    percent_error DECIMAL(10,2), -- Calculated: ((user_value - ai_value) / user_value) * 100
    absolute_error DECIMAL(10,2), -- Calculated: ABS(user_value - ai_value)

    -- Context about the meal when correction was made
    confidence_score DECIMAL(3,2), -- AI's confidence when it made the prediction
    location_type VARCHAR(50), -- 'restaurant', 'home', 'cafe', etc. (from meals.location_place_type)
    location_place_name VARCHAR(255), -- Restaurant name or 'Home' (from meals.location_place_name)
    meal_type VARCHAR(20), -- 'breakfast', 'lunch', 'dinner', 'snack'
    meal_description TEXT, -- What the meal was (for pattern analysis)

    -- Timestamps
    ai_analyzed_at TIMESTAMP, -- When AI made the original prediction
    corrected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- When user made the correction

    -- Indexes for analytics queries
    CONSTRAINT fk_ai_correction_meal FOREIGN KEY (meal_id) REFERENCES meals(id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_correction_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Indexes for common analytics queries
CREATE INDEX idx_ai_correction_field_name ON ai_correction_logs(field_name);
CREATE INDEX idx_ai_correction_user_id ON ai_correction_logs(user_id);
CREATE INDEX idx_ai_correction_meal_id ON ai_correction_logs(meal_id);
CREATE INDEX idx_ai_correction_location_type ON ai_correction_logs(location_type);
CREATE INDEX idx_ai_correction_corrected_at ON ai_correction_logs(corrected_at);
CREATE INDEX idx_ai_correction_confidence ON ai_correction_logs(confidence_score);

-- Composite index for user-specific accuracy analysis
CREATE INDEX idx_ai_correction_user_field ON ai_correction_logs(user_id, field_name);

-- Composite index for location-based accuracy analysis
CREATE INDEX idx_ai_correction_location_field ON ai_correction_logs(location_type, field_name);

-- Comments for documentation
COMMENT ON TABLE ai_correction_logs IS 'Tracks user corrections to AI predictions for accuracy measurement and bias detection';
COMMENT ON COLUMN ai_correction_logs.field_name IS 'Name of the nutrition field that was corrected (e.g., calories, protein_g)';
COMMENT ON COLUMN ai_correction_logs.percent_error IS 'Percentage error: ((user_value - ai_value) / user_value) * 100. Positive = AI underestimated, Negative = AI overestimated';
COMMENT ON COLUMN ai_correction_logs.confidence_score IS 'AI confidence score (0.0-1.0) when the prediction was made';
COMMENT ON COLUMN ai_correction_logs.location_type IS 'Type of location where meal was consumed (for analyzing AI accuracy by location)';
