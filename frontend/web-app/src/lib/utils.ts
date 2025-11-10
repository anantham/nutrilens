import { type ClassValue, clsx } from 'clsx';

/**
 * Utility for merging Tailwind classes
 */
export function cn(...inputs: ClassValue[]) {
  return clsx(inputs);
}

/**
 * Format date to readable string
 */
export function formatDate(date: string | Date): string {
  const d = typeof date === 'string' ? new Date(date) : date;
  return d.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  });
}

/**
 * Format date and time
 */
export function formatDateTime(date: string | Date): string {
  const d = typeof date === 'string' ? new Date(date) : date;
  return d.toLocaleString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/**
 * Format calories with commas
 */
export function formatCalories(calories: number): string {
  return calories.toLocaleString();
}

/**
 * Format macronutrient (protein, fat, carbs)
 */
export function formatMacro(value: number, unit: string = 'g'): string {
  return `${value.toFixed(1)}${unit}`;
}

/**
 * Calculate percentage of daily value
 */
export function calculatePercentage(value: number, total: number): number {
  if (total === 0) return 0;
  return Math.round((value / total) * 100);
}

/**
 * Get meal type display name
 */
export function getMealTypeName(type: string): string {
  const names: Record<string, string> = {
    BREAKFAST: 'Breakfast',
    LUNCH: 'Lunch',
    DINNER: 'Dinner',
    SNACK: 'Snack',
  };
  return names[type] || type;
}

/**
 * Get meal type color (for badges)
 */
export function getMealTypeColor(type: string): string {
  const colors: Record<string, string> = {
    BREAKFAST: 'bg-yellow-100 text-yellow-800',
    LUNCH: 'bg-blue-100 text-blue-800',
    DINNER: 'bg-purple-100 text-purple-800',
    SNACK: 'bg-green-100 text-green-800',
  };
  return colors[type] || 'bg-gray-100 text-gray-800';
}

/**
 * Get confidence level text and color
 */
export function getConfidenceLevel(confidence: number): {
  text: string;
  color: string;
} {
  if (confidence >= 0.8) {
    return { text: 'High', color: 'text-green-600' };
  } else if (confidence >= 0.6) {
    return { text: 'Medium', color: 'text-yellow-600' };
  } else {
    return { text: 'Low', color: 'text-red-600' };
  }
}

/**
 * Validate file is an image
 */
export function isValidImageFile(file: File): boolean {
  const validTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/heic', 'image/webp'];
  return validTypes.includes(file.type);
}

/**
 * Format file size to human readable
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 Bytes';

  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));

  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}

/**
 * Debounce function
 */
export function debounce<T extends (...args: any[]) => any>(
  func: T,
  wait: number
): (...args: Parameters<T>) => void {
  let timeout: NodeJS.Timeout | null = null;

  return function (...args: Parameters<T>) {
    if (timeout) clearTimeout(timeout);
    timeout = setTimeout(() => func(...args), wait);
  };
}

/**
 * Calculate BMI
 */
export function calculateBMI(weightKg: number, heightCm: number): number {
  const heightM = heightCm / 100;
  return weightKg / (heightM * heightM);
}

/**
 * Get BMI category
 */
export function getBMICategory(bmi: number): string {
  if (bmi < 18.5) return 'Underweight';
  if (bmi < 25) return 'Normal weight';
  if (bmi < 30) return 'Overweight';
  return 'Obese';
}
