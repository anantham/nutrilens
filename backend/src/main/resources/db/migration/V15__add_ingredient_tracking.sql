-- V15: Add ingredient-level decomposition and learning
-- This allows users to see and correct individual ingredients, building a personal ingredient database

-- Ingredient decomposition for each meal
CREATE TABLE meal_ingredients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meal_id UUID NOT NULL REFERENCES meals(id) ON DELETE CASCADE,

    -- Ingredient identification
    ingredient_name VARCHAR(200) NOT NULL,
    ingredient_category VARCHAR(50),  -- "grain", "protein", "fat", "vegetable", "spice"

    -- Quantity
    quantity DECIMAL(10,2) NOT NULL,
    unit VARCHAR(20) NOT NULL,        -- "g", "ml", "tsp", "cup", "piece"

    -- Nutrition per serving (calculated or user-corrected)
    calories DECIMAL(10,2),
    protein_g DECIMAL(10,2),
    fat_g DECIMAL(10,2),
    saturated_fat_g DECIMAL(10,2),
    carbohydrates_g DECIMAL(10,2),
    fiber_g DECIMAL(10,2),
    sugar_g DECIMAL(10,2),
    sodium_mg DECIMAL(10,2),

    -- AI vs User corrections
    is_ai_extracted BOOLEAN DEFAULT TRUE,
    is_user_corrected BOOLEAN DEFAULT FALSE,
    ai_confidence DECIMAL(3,2),

    -- Ordering for display
    display_order INTEGER DEFAULT 0,

    created_at TIMESTAMP DEFAULT NOW()
);

-- Personal ingredient database (learned from user corrections)
CREATE TABLE user_ingredient_library (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Ingredient identification
    ingredient_name VARCHAR(200) NOT NULL,
    ingredient_category VARCHAR(50),
    normalized_name VARCHAR(200),     -- "ghee" and "clarified butter" → "ghee"

    -- Average nutrition per 100g/100ml (learned from user corrections)
    avg_calories_per_100g DECIMAL(10,2),
    avg_protein_per_100g DECIMAL(10,2),
    avg_fat_per_100g DECIMAL(10,2),
    avg_carbs_per_100g DECIMAL(10,2),

    -- Standard deviation (variability in user's corrections)
    std_dev_calories DECIMAL(10,2),

    -- Learning metadata
    sample_size INTEGER DEFAULT 1,     -- How many times user has corrected this ingredient
    confidence_score DECIMAL(3,2),     -- Higher with more samples
    last_used TIMESTAMP DEFAULT NOW(),
    created_at TIMESTAMP DEFAULT NOW(),

    -- Typical quantity for this user
    typical_quantity DECIMAL(10,2),
    typical_unit VARCHAR(20),

    CONSTRAINT unique_user_ingredient UNIQUE(user_id, normalized_name)
);

-- Recipe patterns (e.g., "when user makes idli, they typically use these ingredients")
CREATE TABLE user_recipe_patterns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Recipe identification (fuzzy matching)
    recipe_name VARCHAR(200) NOT NULL,  -- "idli", "dosa", "sambar"
    recipe_keywords TEXT[],              -- ["idli", "breakfast", "fermented"]

    -- Common ingredients for this recipe
    common_ingredients JSONB,            -- [{"name":"rice batter","qty":100,"unit":"g"}, ...]

    -- Frequency
    times_made INTEGER DEFAULT 1,
    last_made TIMESTAMP DEFAULT NOW(),

    CONSTRAINT unique_user_recipe UNIQUE(user_id, recipe_name)
);

-- Indexes for fast lookups
CREATE INDEX idx_meal_ingredients_meal ON meal_ingredients(meal_id);
CREATE INDEX idx_meal_ingredients_name ON meal_ingredients(ingredient_name);
CREATE INDEX idx_user_ingredient_library_user ON user_ingredient_library(user_id);
CREATE INDEX idx_user_ingredient_library_name ON user_ingredient_library(normalized_name);
CREATE INDEX idx_user_recipe_patterns_user ON user_recipe_patterns(user_id);
CREATE INDEX idx_user_recipe_patterns_name ON user_recipe_patterns(recipe_name);

-- Comments
COMMENT ON TABLE meal_ingredients IS 'Ingredient decomposition for each meal - AI extracted and user corrected';
COMMENT ON TABLE user_ingredient_library IS 'Personal ingredient database learned from user corrections over time';
COMMENT ON TABLE user_recipe_patterns IS 'Recipe patterns learned from user behavior (e.g., typical idli ingredients)';

COMMENT ON COLUMN meal_ingredients.is_user_corrected IS 'True if user manually edited this ingredient';
COMMENT ON COLUMN user_ingredient_library.sample_size IS 'Number of times user has corrected this ingredient (higher = more reliable)';
COMMENT ON COLUMN user_ingredient_library.confidence_score IS 'Confidence in nutrition values (0-1), increases with sample_size';
