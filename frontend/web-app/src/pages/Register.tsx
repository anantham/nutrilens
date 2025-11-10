import { useState, FormEvent } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '@/lib/auth';
import { authApi, profileApi, goalsApi } from '@/lib/api';
import { RegisterForm, ProfileForm, GoalForm } from '@/types';
import toast from 'react-hot-toast';

type Step = 'account' | 'profile' | 'goals';

export default function Register() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [currentStep, setCurrentStep] = useState<Step>('account');
  const [isLoading, setIsLoading] = useState(false);

  // Account data
  const [accountData, setAccountData] = useState<RegisterForm>({
    email: '',
    password: '',
    passwordConfirm: '',
  });
  const [accountErrors, setAccountErrors] = useState<Record<string, string>>({});

  // Profile data
  const [profileData, setProfileData] = useState<ProfileForm>({
    firstName: '',
    lastName: '',
    dateOfBirth: '',
    gender: 'MALE',
    heightCm: 170,
    weightKg: 70,
  });
  const [profileErrors, setProfileErrors] = useState<Record<string, string>>({});

  // Goals data
  const [goalsData, setGoalsData] = useState<GoalForm>({
    dailyCalories: 2000,
    dailyProteinG: 150,
    dailyFatG: 65,
    dailyCarbsG: 250,
  });

  // Validate account step
  const validateAccount = (): boolean => {
    const errors: Record<string, string> = {};

    if (!accountData.email.trim()) {
      errors.email = 'Email is required';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(accountData.email)) {
      errors.email = 'Invalid email format';
    }

    if (!accountData.password) {
      errors.password = 'Password is required';
    } else if (accountData.password.length < 8) {
      errors.password = 'Password must be at least 8 characters';
    }

    if (accountData.password !== accountData.passwordConfirm) {
      errors.passwordConfirm = 'Passwords do not match';
    }

    setAccountErrors(errors);
    return Object.keys(errors).length === 0;
  };

  // Validate profile step
  const validateProfile = (): boolean => {
    const errors: Record<string, string> = {};

    if (!profileData.firstName.trim()) {
      errors.firstName = 'First name is required';
    }
    if (!profileData.lastName.trim()) {
      errors.lastName = 'Last name is required';
    }
    if (!profileData.dateOfBirth) {
      errors.dateOfBirth = 'Date of birth is required';
    }
    if (profileData.heightCm < 100 || profileData.heightCm > 250) {
      errors.heightCm = 'Height must be between 100-250 cm';
    }
    if (profileData.weightKg < 30 || profileData.weightKg > 300) {
      errors.weightKg = 'Weight must be between 30-300 kg';
    }

    setProfileErrors(errors);
    return Object.keys(errors).length === 0;
  };

  // Handle account step submission
  const handleAccountSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (validateAccount()) {
      setCurrentStep('profile');
    }
  };

  // Handle profile step submission
  const handleProfileSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (validateProfile()) {
      setCurrentStep('goals');
    }
  };

  // Handle final registration
  const handleGoalsSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setIsLoading(true);

    try {
      // Step 1: Register user account
      const authResponse = await authApi.register({
        email: accountData.email,
        password: accountData.password,
      });

      // Login to get token
      login(authResponse);

      // Step 2: Create user profile
      await profileApi.createProfile(profileData);

      // Step 3: Set nutrition goals
      await goalsApi.updateGoals(goalsData);

      toast.success('Account created successfully!');
      navigate('/');
    } catch (error: any) {
      console.error('Registration error:', error);
      const errorMessage = error.response?.data?.message || 'Registration failed. Please try again.';
      toast.error(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-gradient-to-br from-primary-50 to-primary-100 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-2xl p-8">
        {/* Header */}
        <div className="text-center mb-8">
          <h1 className="text-3xl font-bold text-gray-900">Create Your Account</h1>
          <p className="text-gray-600 mt-2">Start tracking your nutrition with AI</p>
        </div>

        {/* Progress Steps */}
        <div className="flex items-center justify-between mb-8">
          <StepIndicator
            number={1}
            label="Account"
            isActive={currentStep === 'account'}
            isCompleted={currentStep === 'profile' || currentStep === 'goals'}
          />
          <div className="flex-1 h-1 bg-gray-200 mx-2">
            <div
              className={`h-full bg-primary-600 transition-all ${
                currentStep === 'profile' || currentStep === 'goals' ? 'w-full' : 'w-0'
              }`}
            />
          </div>
          <StepIndicator
            number={2}
            label="Profile"
            isActive={currentStep === 'profile'}
            isCompleted={currentStep === 'goals'}
          />
          <div className="flex-1 h-1 bg-gray-200 mx-2">
            <div
              className={`h-full bg-primary-600 transition-all ${
                currentStep === 'goals' ? 'w-full' : 'w-0'
              }`}
            />
          </div>
          <StepIndicator
            number={3}
            label="Goals"
            isActive={currentStep === 'goals'}
            isCompleted={false}
          />
        </div>

        {/* Account Step */}
        {currentStep === 'account' && (
          <form onSubmit={handleAccountSubmit} className="space-y-6">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Email Address</label>
              <input
                type="email"
                value={accountData.email}
                onChange={(e) => setAccountData({ ...accountData, email: e.target.value })}
                className={`w-full px-4 py-3 border rounded-lg focus:ring-2 focus:ring-primary-500 ${
                  accountErrors.email ? 'border-red-500' : 'border-gray-300'
                }`}
                placeholder="you@example.com"
              />
              {accountErrors.email && <p className="mt-1 text-sm text-red-600">{accountErrors.email}</p>}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Password</label>
              <input
                type="password"
                value={accountData.password}
                onChange={(e) => setAccountData({ ...accountData, password: e.target.value })}
                className={`w-full px-4 py-3 border rounded-lg focus:ring-2 focus:ring-primary-500 ${
                  accountErrors.password ? 'border-red-500' : 'border-gray-300'
                }`}
                placeholder="At least 8 characters"
              />
              {accountErrors.password && <p className="mt-1 text-sm text-red-600">{accountErrors.password}</p>}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Confirm Password</label>
              <input
                type="password"
                value={accountData.passwordConfirm}
                onChange={(e) => setAccountData({ ...accountData, passwordConfirm: e.target.value })}
                className={`w-full px-4 py-3 border rounded-lg focus:ring-2 focus:ring-primary-500 ${
                  accountErrors.passwordConfirm ? 'border-red-500' : 'border-gray-300'
                }`}
                placeholder="Re-enter your password"
              />
              {accountErrors.passwordConfirm && <p className="mt-1 text-sm text-red-600">{accountErrors.passwordConfirm}</p>}
            </div>

            <button
              type="submit"
              className="w-full bg-primary-600 text-white py-3 rounded-lg font-semibold hover:bg-primary-700 transition"
            >
              Continue
            </button>
          </form>
        )}

        {/* Profile Step */}
        {currentStep === 'profile' && (
          <form onSubmit={handleProfileSubmit} className="space-y-6">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">First Name</label>
                <input
                  type="text"
                  value={profileData.firstName}
                  onChange={(e) => setProfileData({ ...profileData, firstName: e.target.value })}
                  className={`w-full px-4 py-3 border rounded-lg focus:ring-2 focus:ring-primary-500 ${
                    profileErrors.firstName ? 'border-red-500' : 'border-gray-300'
                  }`}
                />
                {profileErrors.firstName && <p className="mt-1 text-sm text-red-600">{profileErrors.firstName}</p>}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Last Name</label>
                <input
                  type="text"
                  value={profileData.lastName}
                  onChange={(e) => setProfileData({ ...profileData, lastName: e.target.value })}
                  className={`w-full px-4 py-3 border rounded-lg focus:ring-2 focus:ring-primary-500 ${
                    profileErrors.lastName ? 'border-red-500' : 'border-gray-300'
                  }`}
                />
                {profileErrors.lastName && <p className="mt-1 text-sm text-red-600">{profileErrors.lastName}</p>}
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Date of Birth</label>
              <input
                type="date"
                value={profileData.dateOfBirth}
                onChange={(e) => setProfileData({ ...profileData, dateOfBirth: e.target.value })}
                className={`w-full px-4 py-3 border rounded-lg focus:ring-2 focus:ring-primary-500 ${
                  profileErrors.dateOfBirth ? 'border-red-500' : 'border-gray-300'
                }`}
              />
              {profileErrors.dateOfBirth && <p className="mt-1 text-sm text-red-600">{profileErrors.dateOfBirth}</p>}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">Gender</label>
              <select
                value={profileData.gender}
                onChange={(e) => setProfileData({ ...profileData, gender: e.target.value as any })}
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
              >
                <option value="MALE">Male</option>
                <option value="FEMALE">Female</option>
                <option value="OTHER">Other</option>
              </select>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Height (cm)</label>
                <input
                  type="number"
                  value={profileData.heightCm}
                  onChange={(e) => setProfileData({ ...profileData, heightCm: parseInt(e.target.value) })}
                  className={`w-full px-4 py-3 border rounded-lg focus:ring-2 focus:ring-primary-500 ${
                    profileErrors.heightCm ? 'border-red-500' : 'border-gray-300'
                  }`}
                  min={100}
                  max={250}
                />
                {profileErrors.heightCm && <p className="mt-1 text-sm text-red-600">{profileErrors.heightCm}</p>}
              </div>

              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">Weight (kg)</label>
                <input
                  type="number"
                  value={profileData.weightKg}
                  onChange={(e) => setProfileData({ ...profileData, weightKg: parseInt(e.target.value) })}
                  className={`w-full px-4 py-3 border rounded-lg focus:ring-2 focus:ring-primary-500 ${
                    profileErrors.weightKg ? 'border-red-500' : 'border-gray-300'
                  }`}
                  min={30}
                  max={300}
                />
                {profileErrors.weightKg && <p className="mt-1 text-sm text-red-600">{profileErrors.weightKg}</p>}
              </div>
            </div>

            <div className="flex space-x-4">
              <button
                type="button"
                onClick={() => setCurrentStep('account')}
                className="flex-1 bg-gray-200 text-gray-700 py-3 rounded-lg font-semibold hover:bg-gray-300 transition"
              >
                Back
              </button>
              <button
                type="submit"
                className="flex-1 bg-primary-600 text-white py-3 rounded-lg font-semibold hover:bg-primary-700 transition"
              >
                Continue
              </button>
            </div>
          </form>
        )}

        {/* Goals Step */}
        {currentStep === 'goals' && (
          <form onSubmit={handleGoalsSubmit} className="space-y-6">
            <p className="text-gray-600 text-center mb-6">
              Set your daily nutrition goals. You can adjust these later.
            </p>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Daily Calories: {goalsData.dailyCalories} kcal
              </label>
              <input
                type="range"
                min={1200}
                max={4000}
                step={100}
                value={goalsData.dailyCalories}
                onChange={(e) => setGoalsData({ ...goalsData, dailyCalories: parseInt(e.target.value) })}
                className="w-full"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Daily Protein: {goalsData.dailyProteinG}g
              </label>
              <input
                type="range"
                min={50}
                max={300}
                step={10}
                value={goalsData.dailyProteinG}
                onChange={(e) => setGoalsData({ ...goalsData, dailyProteinG: parseInt(e.target.value) })}
                className="w-full"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Daily Fat: {goalsData.dailyFatG}g
              </label>
              <input
                type="range"
                min={30}
                max={150}
                step={5}
                value={goalsData.dailyFatG}
                onChange={(e) => setGoalsData({ ...goalsData, dailyFatG: parseInt(e.target.value) })}
                className="w-full"
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Daily Carbs: {goalsData.dailyCarbsG}g
              </label>
              <input
                type="range"
                min={50}
                max={500}
                step={10}
                value={goalsData.dailyCarbsG}
                onChange={(e) => setGoalsData({ ...goalsData, dailyCarbsG: parseInt(e.target.value) })}
                className="w-full"
              />
            </div>

            <div className="flex space-x-4">
              <button
                type="button"
                onClick={() => setCurrentStep('profile')}
                disabled={isLoading}
                className="flex-1 bg-gray-200 text-gray-700 py-3 rounded-lg font-semibold hover:bg-gray-300 transition disabled:opacity-50"
              >
                Back
              </button>
              <button
                type="submit"
                disabled={isLoading}
                className="flex-1 bg-primary-600 text-white py-3 rounded-lg font-semibold hover:bg-primary-700 transition disabled:opacity-50"
              >
                {isLoading ? 'Creating Account...' : 'Create Account'}
              </button>
            </div>
          </form>
        )}

        {/* Footer */}
        <div className="mt-6 text-center">
          <p className="text-sm text-gray-600">
            Already have an account?{' '}
            <Link to="/login" className="text-primary-600 hover:text-primary-700 font-semibold">
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}

// Step Indicator Component
function StepIndicator({
  number,
  label,
  isActive,
  isCompleted,
}: {
  number: number;
  label: string;
  isActive: boolean;
  isCompleted: boolean;
}) {
  return (
    <div className="flex flex-col items-center">
      <div
        className={`w-10 h-10 rounded-full flex items-center justify-center font-semibold transition ${
          isCompleted
            ? 'bg-primary-600 text-white'
            : isActive
            ? 'bg-primary-600 text-white'
            : 'bg-gray-200 text-gray-600'
        }`}
      >
        {isCompleted ? '✓' : number}
      </div>
      <span className={`text-xs mt-1 ${isActive ? 'text-primary-600 font-semibold' : 'text-gray-500'}`}>
        {label}
      </span>
    </div>
  );
}
