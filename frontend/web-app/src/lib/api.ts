import axios, { AxiosError } from 'axios';
import type {
  AuthResponse,
  LoginForm,
  RegisterForm,
  MealResponse,
  PageResponse,
  MealUploadForm,
  FieldAccuracyStats,
} from '@/types';

// Create axios instance
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add auth token to requests
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Handle unauthorized responses
api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('auth_token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// ==================== Auth API ====================

export const authApi = {
  login: async (data: LoginForm): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/api/auth/login', data);
    return response.data;
  },

  register: async (data: RegisterForm): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/api/auth/register', data);
    return response.data;
  },

  logout: () => {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('user');
  },
};

// ==================== Meals API ====================

export const mealsApi = {
  // Upload meal with image and optional metadata
  uploadMeal: async (data: MealUploadForm): Promise<MealResponse> => {
    const formData = new FormData();

    if (data.image) {
      formData.append('image', data.image);
    }
    if (data.mealType) {
      formData.append('mealType', data.mealType);
    }
    if (data.mealTime) {
      formData.append('mealTime', data.mealTime);
    }
    if (data.description) {
      formData.append('description', data.description);
    }

    const response = await api.post<MealResponse>('/api/meals/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });

    return response.data;
  },

  // Get paginated meals
  getMealsPaginated: async (page: number = 0, size: number = 20): Promise<PageResponse<MealResponse>> => {
    const response = await api.get<PageResponse<MealResponse>>('/api/meals/paginated', {
      params: { page, size },
    });
    return response.data;
  },

  // Get meals by date range (paginated)
  getMealsByDateRange: async (
    startDate: string,
    endDate: string,
    page: number = 0,
    size: number = 20
  ): Promise<PageResponse<MealResponse>> => {
    const response = await api.get<PageResponse<MealResponse>>('/api/meals/paginated/range', {
      params: { startDate, endDate, page, size },
    });
    return response.data;
  },

  // Get meals by type (paginated)
  getMealsByType: async (
    mealType: string,
    page: number = 0,
    size: number = 20
  ): Promise<PageResponse<MealResponse>> => {
    const response = await api.get<PageResponse<MealResponse>>(`/api/meals/paginated/type/${mealType}`, {
      params: { page, size },
    });
    return response.data;
  },

  // Get single meal by ID
  getMealById: async (id: string): Promise<MealResponse> => {
    const response = await api.get<MealResponse>(`/api/meals/${id}`);
    return response.data;
  },

  // Update meal
  updateMeal: async (id: string, data: Partial<MealResponse>): Promise<MealResponse> => {
    const response = await api.put<MealResponse>(`/api/meals/${id}`, data);
    return response.data;
  },

  // Delete meal
  deleteMeal: async (id: string): Promise<void> => {
    await api.delete(`/api/meals/${id}`);
  },

  // Get total meal count
  getMealsCount: async (): Promise<number> => {
    const response = await api.get<number>('/api/meals/count');
    return response.data;
  },
};

// ==================== Analytics API ====================

export const analyticsApi = {
  // Get overall AI accuracy stats
  getOverallAccuracy: async (): Promise<FieldAccuracyStats[]> => {
    const response = await api.get<FieldAccuracyStats[]>('/api/analytics/corrections/overall-accuracy');
    return response.data;
  },

  // Get systematic bias detection
  getSystematicBias: async (): Promise<FieldAccuracyStats[]> => {
    const response = await api.get<FieldAccuracyStats[]>('/api/analytics/corrections/systematic-bias');
    return response.data;
  },

  // Get accuracy by location type
  getAccuracyByLocation: async (): Promise<any[]> => {
    const response = await api.get('/api/analytics/corrections/accuracy-by-location');
    return response.data;
  },

  // Get correction trends
  getCorrectionTrends: async (days: number = 30): Promise<any[]> => {
    const response = await api.get('/api/analytics/corrections/trends', {
      params: { days },
    });
    return response.data;
  },
};

// ==================== Health Check API ====================

export const healthApi = {
  check: async (): Promise<any> => {
    const response = await api.get('/actuator/health');
    return response.data;
  },
};

export default api;
