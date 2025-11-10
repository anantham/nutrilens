import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, ProtectedRoute } from '@/lib/auth';

// Lazy load pages for code splitting
import { lazy, Suspense } from 'react';

const Login = lazy(() => import('@/pages/Login'));
const Register = lazy(() => import('@/pages/Register'));
const Dashboard = lazy(() => import('@/pages/Dashboard'));
const MealUpload = lazy(() => import('@/pages/MealUpload'));
const MealList = lazy(() => import('@/pages/MealList'));
const MealDetail = lazy(() => import('@/pages/MealDetail'));
const Analytics = lazy(() => import('@/pages/Analytics'));
const Layout = lazy(() => import('@/components/Layout'));

// Loading fallback component
function LoadingFallback() {
  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-50">
      <div className="flex flex-col items-center space-y-4">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
        <p className="text-gray-600">Loading...</p>
      </div>
    </div>
  );
}

function App() {
  return (
    <AuthProvider>
      <Suspense fallback={<LoadingFallback />}>
        <Routes>
          {/* Public routes */}
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />

          {/* Protected routes with layout */}
          <Route
            element={
              <ProtectedRoute>
                <Layout />
              </ProtectedRoute>
            }
          >
            <Route path="/" element={<Dashboard />} />
            <Route path="/meals/upload" element={<MealUpload />} />
            <Route path="/meals" element={<MealList />} />
            <Route path="/meals/:id" element={<MealDetail />} />
            <Route path="/analytics" element={<Analytics />} />
          </Route>

          {/* Fallback redirect */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </AuthProvider>
  );
}

export default App;
