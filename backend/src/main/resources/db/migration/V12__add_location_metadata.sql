-- Add photo metadata and location context fields for intelligent meal analysis
-- GPS coordinates + Google Maps data provide context to AI for better nutrition estimates

ALTER TABLE meals
    -- Photo metadata (EXIF data)
    ADD COLUMN photo_captured_at TIMESTAMP,
    ADD COLUMN photo_latitude DECIMAL(10, 7),
    ADD COLUMN photo_longitude DECIMAL(10, 7),
    ADD COLUMN photo_device_make VARCHAR(100),
    ADD COLUMN photo_device_model VARCHAR(100),

    -- Location context (from Google Maps API)
    ADD COLUMN location_place_name VARCHAR(255),
    ADD COLUMN location_place_type VARCHAR(50),
    ADD COLUMN location_cuisine_type VARCHAR(50),
    ADD COLUMN location_price_level INTEGER CHECK (location_price_level IS NULL OR (location_price_level BETWEEN 1 AND 4)),
    ADD COLUMN location_is_restaurant BOOLEAN DEFAULT FALSE,
    ADD COLUMN location_is_home BOOLEAN DEFAULT FALSE,
    ADD COLUMN location_address VARCHAR(500);

-- Add comments for new fields
COMMENT ON COLUMN meals.photo_captured_at IS 'Timestamp when photo was taken (from EXIF DateTimeOriginal)';
COMMENT ON COLUMN meals.photo_latitude IS 'GPS latitude from EXIF (WGS84)';
COMMENT ON COLUMN meals.photo_longitude IS 'GPS longitude from EXIF (WGS84)';
COMMENT ON COLUMN meals.photo_device_make IS 'Camera/phone manufacturer (e.g., Apple, Samsung)';
COMMENT ON COLUMN meals.photo_device_model IS 'Camera/phone model (e.g., iPhone 15 Pro)';

COMMENT ON COLUMN meals.location_place_name IS 'Name of place from Google Maps (e.g., Chipotle, Home)';
COMMENT ON COLUMN meals.location_place_type IS 'Type of location: restaurant/cafe/home/gym/office/etc';
COMMENT ON COLUMN meals.location_cuisine_type IS 'Cuisine type if restaurant (mexican/italian/chinese/etc)';
COMMENT ON COLUMN meals.location_price_level IS 'Google Maps price level (1=$ to 4=$$$$)';
COMMENT ON COLUMN meals.location_is_restaurant IS 'Whether meal was consumed at a restaurant/cafe';
COMMENT ON COLUMN meals.location_is_home IS 'Whether meal was consumed at home (residential area)';
COMMENT ON COLUMN meals.location_address IS 'Full formatted address from Google Maps';

-- Create index for location-based queries
CREATE INDEX idx_meals_location_place_type ON meals(location_place_type) WHERE location_place_type IS NOT NULL;
CREATE INDEX idx_meals_location_is_restaurant ON meals(location_is_restaurant) WHERE location_is_restaurant = TRUE;
CREATE INDEX idx_meals_location_is_home ON meals(location_is_home) WHERE location_is_home = TRUE;
