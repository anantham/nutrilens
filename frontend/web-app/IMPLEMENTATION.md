# Nutritheous Web App - Implementation Guide

## Current Status: 🟡 In Progress

### ✅ Completed
1. **Project Setup**
   - Vite + React + TypeScript configuration
   - TailwindCSS setup
   - React Query configuration
   - Axios API client setup
   - Environment configuration

2. **Type Definitions**
   - Complete TypeScript interfaces for API responses
   - Form types for all input scenarios
   - Page response wrapper for pagination

3. **API Client**
   - Authentication API (login, register, logout)
   - Meals API (upload, get, update, delete, pagination)
   - Analytics API (accuracy stats, bias detection, trends)
   - Axios interceptors for auth token injection
   - Auto-logout on 401 responses

4. **Authentication System**
   - AuthContext for global auth state
   - Protected Route component
   - LocalStorage persistence
   - JWT token management

### 🚧 To Be Implemented

#### 1. Main App Component (`src/App.tsx`)
```typescript
import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, ProtectedRoute } from '@/lib/auth';

// Pages
import Login from '@/pages/Login';
import Register from '@/pages/Register';
import Dashboard from '@/pages/Dashboard';
import MealUpload from '@/pages/MealUpload';
import MealList from '@/pages/MealList';
import MealDetail from '@/pages/MealDetail';
import Analytics from '@/pages/Analytics';
import Layout from '@/components/Layout';

function App() {
  return (
    <AuthProvider>
      <Routes>
        {/* Public routes */}
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />

        {/* Protected routes */}
        <Route element={<Layout />}>
          <Route path="/" element={
            <ProtectedRoute>
              <Dashboard />
            </ProtectedRoute>
          } />
          <Route path="/meals/upload" element={
            <ProtectedRoute>
              <MealUpload />
            </ProtectedRoute>
          } />
          <Route path="/meals" element={
            <ProtectedRoute>
              <MealList />
            </ProtectedRoute>
          } />
          <Route path="/meals/:id" element={
            <ProtectedRoute>
              <MealDetail />
            </ProtectedRoute>
          } />
          <Route path="/analytics" element={
            <ProtectedRoute>
              <Analytics />
            </ProtectedRoute>
          } />
        </Route>

        {/* Redirect */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  );
}

export default App;
```

#### 2. Login Page (`src/pages/Login.tsx`)
**Features**:
- Email + password form
- Form validation
- Error handling
- "Remember me" option
- Link to registration
- Loading state

**Key Implementation**:
- Use `react-hook-form` or manual state
- Call `authApi.login()`
- Store token with `useAuth().login()`
- Navigate to dashboard on success

#### 3. Register Page (`src/pages/Register.tsx`)
**Features**:
- Multi-step form:
  1. Email + password
  2. Personal info (age, height, weight)
  3. Activity level selection
- Form validation
- Password strength indicator
- Link to login

#### 4. Dashboard (`src/pages/Dashboard.tsx`)
**Features**:
- Today's nutrition summary
- Calories consumed vs target
- Macro breakdown (protein, fat, carbs)
- Recent meals (last 5)
- Quick action buttons (upload meal)
- Weekly chart

**API Calls**:
- `mealsApi.getMealsByDateRange(today, today)`
- `mealsApi.getMealsPaginated(0, 5)` for recent

#### 5. Meal Upload (`src/pages/MealUpload.tsx`)
**Features**:
- Drag-and-drop file upload
- Image preview
- Meal type selection (dropdown)
- Optional description
- Submit button
- Progress indicator during upload
- Success/error feedback

**Implementation**:
```typescript
import { useDropzone } from 'react-dropzone';
import { useMutation } from '@tanstack/react-query';
import { mealsApi } from '@/lib/api';

// Dropzone for image
const { getRootProps, getInputProps, isDragActive } = useDropzone({
  accept: { 'image/*': ['.png', '.jpg', '.jpeg', '.heic'] },
  maxSize: 10 * 1024 * 1024, // 10MB
  multiple: false,
  onDrop: (files) => setImage(files[0]),
});

// Mutation for upload
const uploadMutation = useMutation({
  mutationFn: mealsApi.uploadMeal,
  onSuccess: (data) => {
    toast.success('Meal uploaded successfully!');
    navigate(`/meals/${data.id}`);
  },
  onError: (error) => {
    toast.error('Upload failed');
  },
});
```

#### 6. Meal List (`src/pages/MealList.tsx`)
**Features**:
- Paginated meal list
- Filter by meal type
- Filter by date range
- Search by description
- Sort by date
- Meal cards with preview
- Load more button or infinite scroll

**Implementation**:
```typescript
import { useInfiniteQuery } from '@tanstack/react-query';

const {
  data,
  fetchNextPage,
  hasNextPage,
  isFetchingNextPage,
} = useInfiniteQuery({
  queryKey: ['meals'],
  queryFn: ({ pageParam = 0 }) => mealsApi.getMealsPaginated(pageParam, 20),
  getNextPageParam: (lastPage) =>
    lastPage.hasNext ? lastPage.page + 1 : undefined,
});

// Flatten pages
const meals = data?.pages.flatMap(page => page.content) ?? [];
```

#### 7. Meal Detail (`src/pages/MealDetail.tsx`)
**Features**:
- Full meal information
- Large image display
- Complete nutrition breakdown
- Location context (if available)
- AI confidence score
- Edit button
- Delete button
- Correction interface

#### 8. Analytics Page (`src/pages/Analytics.tsx`)
**Features**:
- AI accuracy by field (bar chart)
- Systematic bias detection
- Location-based accuracy
- Correction trends over time
- Total corrections count
- Most corrected fields

**Charts**:
- Bar chart: Accuracy by field
- Line chart: Correction trends
- Pie chart: Corrections by meal type

#### 9. Layout Component (`src/components/Layout.tsx`)
**Features**:
- Top navigation bar
  - Logo
  - Navigation links
  - User menu (profile, logout)
- Sidebar (optional)
- Main content area
- Footer

#### 10. Reusable Components

**MealCard** (`src/components/MealCard.tsx`):
- Meal thumbnail
- Meal name/description
- Calories
- Time
- Click to view details

**NutritionBar** (`src/components/NutritionBar.tsx`):
- Horizontal bar showing macros
- Color-coded (protein, fat, carbs)
- Percentage breakdown

**LoadingSpinner** (`src/components/LoadingSpinner.tsx`):
- Reusable loading indicator
- Different sizes

**Button** (`src/components/Button.tsx`):
- Primary, secondary, danger variants
- Loading state
- Disabled state

## Installation & Setup

```bash
# Navigate to web-app directory
cd frontend/web-app

# Install dependencies
npm install

# Create .env file
cp .env.example .env

# Start development server
npm run dev
```

## Development Workflow

1. **Start Backend**: Ensure Spring Boot API is running on port 8080
2. **Start Frontend**: Run `npm run dev` (starts on port 3000)
3. **Auto-proxy**: Vite proxies `/api/*` requests to backend
4. **Hot Reload**: Changes auto-refresh in browser

## Testing Checklist

- [ ] Registration flow works
- [ ] Login flow works
- [ ] Meal upload with image works
- [ ] Meal list displays correctly
- [ ] Pagination works
- [ ] Meal details show all information
- [ ] Edit meal saves changes
- [ ] Delete meal removes from list
- [ ] Dashboard shows correct stats
- [ ] Analytics charts render
- [ ] Logout works
- [ ] Auto-logout on 401 works
- [ ] Mobile responsive design works

## File Structure to Create

```
src/
├── App.tsx ✅
├── pages/
│   ├── Login.tsx
│   ├── Register.tsx
│   ├── Dashboard.tsx
│   ├── MealUpload.tsx
│   ├── MealList.tsx
│   ├── MealDetail.tsx
│   └── Analytics.tsx
├── components/
│   ├── Layout.tsx
│   ├── MealCard.tsx
│   ├── NutritionBar.tsx
│   ├── NutritionChart.tsx
│   ├── Button.tsx
│   ├── LoadingSpinner.tsx
│   └── ConfirmDialog.tsx
└── lib/
    └── utils.ts
```

## Next Steps

1. Create `src/App.tsx` with routing
2. Create `src/pages/Login.tsx`
3. Create `src/pages/Dashboard.tsx`
4. Create `src/pages/MealUpload.tsx`
5. Create `src/components/Layout.tsx`
6. Create remaining pages and components
7. Test full user flow
8. Deploy to production

## Estimated Time

- Core pages (Login, Dashboard, MealUpload): 4-6 hours
- Additional pages (MealList, Detail, Analytics): 4-6 hours
- Components and polish: 2-4 hours
- Testing and bug fixes: 2-4 hours

**Total**: 12-20 hours for complete MVP

## Production Deployment

```bash
# Build for production
npm run build

# Test production build locally
npm run preview

# Deploy (example: Vercel)
vercel deploy --prod
```

## Environment Variables for Production

```env
VITE_API_BASE_URL=https://api.nutritheous.com
VITE_APP_NAME=Nutritheous
```

---

**Last Updated**: 2025-11-10
**Status**: Configuration and infrastructure complete, pages implementation in progress
