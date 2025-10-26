-- Add enhanced AI-extracted fields for better nutrition insights
-- These fields are automatically extracted from meal images with no user input required

ALTER TABLE meals
    -- Processing & cooking analysis
    ADD COLUMN cooking_method VARCHAR(50),
    ADD COLUMN nova_score DECIMAL(3,2),
    ADD COLUMN is_ultra_processed BOOLEAN DEFAULT FALSE,
    ADD COLUMN is_fried BOOLEAN DEFAULT FALSE,
    ADD COLUMN has_refined_grains BOOLEAN DEFAULT FALSE,

    -- Glycemic properties
    ADD COLUMN estimated_gi INTEGER,
    ADD COLUMN estimated_gl INTEGER,

    -- Plant diversity & microbiome support
    ADD COLUMN plant_count INTEGER,
    ADD COLUMN unique_plants JSONB,
    ADD COLUMN is_fermented BOOLEAN DEFAULT FALSE,

    -- Protein & fat quality
    ADD COLUMN protein_source_type VARCHAR(50),
    ADD COLUMN fat_quality VARCHAR(20),

    -- Meal type inference
    ADD COLUMN meal_type_guess VARCHAR(20);

-- Add comments for new fields
COMMENT ON COLUMN meals.cooking_method IS 'Detected cooking method: raw/steamed/boiled/grilled/baked/fried/roasted/pressure_cooked';
COMMENT ON COLUMN meals.nova_score IS 'NOVA processing score 1-4 (1=unprocessed, 4=ultra-processed)';
COMMENT ON COLUMN meals.is_ultra_processed IS 'Quick flag for ultra-processed foods (NOVA 4)';
COMMENT ON COLUMN meals.is_fried IS 'Whether the meal contains fried components';
COMMENT ON COLUMN meals.has_refined_grains IS 'Contains refined grains like white bread/rice/pasta';
COMMENT ON COLUMN meals.estimated_gi IS 'Estimated glycemic index (0-100, low<55, med 55-69, high 70+)';
COMMENT ON COLUMN meals.estimated_gl IS 'Estimated glycemic load (low<10, med 10-19, high 20+)';
COMMENT ON COLUMN meals.plant_count IS 'Number of unique plant species in the meal';
COMMENT ON COLUMN meals.unique_plants IS 'List of unique plant names (JSON array)';
COMMENT ON COLUMN meals.is_fermented IS 'Contains fermented foods (yogurt/kimchi/sourdough/etc)';
COMMENT ON COLUMN meals.protein_source_type IS 'Primary protein source: animal/plant/dairy/seafood/mixed';
COMMENT ON COLUMN meals.fat_quality IS 'Fat quality assessment: healthy/neutral/unhealthy';
COMMENT ON COLUMN meals.meal_type_guess IS 'AI-inferred meal type: breakfast/lunch/dinner/snack';
