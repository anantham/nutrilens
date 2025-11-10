// API Response Types
export interface AuthResponse {
  token: string;
  email: string;
  role: string;
  userId: string;
}

export interface MealResponse {
  id: string;
  userId: string;
  mealTime: string;
  mealType: 'BREAKFAST' | 'LUNCH' | 'DINNER' | 'SNACK';
  imageUrl?: string;
  description?: string;
  servingSize?: string;

  // Nutrition
  calories?: number;
  proteinG?: number;
  fatG?: number;
  saturatedFatG?: number;
  carbohydratesG?: number;
  fiberG?: number;
  sugarG?: number;
  sodiumMg?: number;
  cholesterolMg?: number;

  // Metadata
  confidence?: number;
  analysisStatus: 'PENDING' | 'COMPLETED' | 'FAILED';
  ingredients?: string[];
  allergens?: string[];
  healthNotes?: string;

  // Location context
  locationPlaceName?: string;
  locationPlaceType?: string;
  locationIsRestaurant?: boolean;

  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface DailyStats {
  date: string;
  totalCalories: number;
  totalProtein: number;
  totalFat: number;
  totalCarbs: number;
  mealCount: number;
}

export interface User {
  id: string;
  email: string;
  role: string;
  age?: number;
  heightCm?: number;
  weightKg?: number;
  estimatedCaloriesBurntPerDay?: number;
}

// Form Types
export interface LoginForm {
  email: string;
  password: string;
}

export interface RegisterForm {
  email: string;
  password: string;
  age: number;
  heightCm: number;
  weightKg: number;
  sex: 'MALE' | 'FEMALE' | 'OTHER';
  activityLevel: 'SEDENTARY' | 'LIGHT' | 'MODERATE' | 'ACTIVE' | 'VERY_ACTIVE';
}

export interface MealUploadForm {
  image?: File;
  mealType?: 'BREAKFAST' | 'LUNCH' | 'DINNER' | 'SNACK';
  mealTime?: string;
  description?: string;
}

// Analytics Types
export interface FieldAccuracyStats {
  fieldName: string;
  avgAbsPercentError: number;
  correctionCount: number;
  meanAbsoluteError: number;
}
