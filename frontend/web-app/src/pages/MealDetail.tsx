import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { mealsApi } from '@/lib/api';
import { MealUpdateForm } from '@/types';
import {
  formatDateTime,
  formatCalories,
  formatMacro,
  getMealTypeName,
  getMealTypeColor,
  getConfidenceLevel,
} from '@/lib/utils';
import toast from 'react-hot-toast';
import {
  ArrowLeft,
  Edit,
  Trash2,
  Save,
  X,
  Loader2,
  CheckCircle,
  AlertCircle,
  Clock,
} from 'lucide-react';

export default function MealDetail() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [isEditing, setIsEditing] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [editedData, setEditedData] = useState<Partial<MealUpdateForm>>({});

  // Fetch meal details
  const { data: meal, isLoading, isError } = useQuery({
    queryKey: ['meal', id],
    queryFn: () => mealsApi.getMealById(id!),
    enabled: !!id,
  });

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: (data: MealUpdateForm) => mealsApi.updateMeal(id!, data),
    onSuccess: () => {
      toast.success('Meal updated successfully');
      queryClient.invalidateQueries({ queryKey: ['meal', id] });
      queryClient.invalidateQueries({ queryKey: ['meals'] });
      setIsEditing(false);
    },
    onError: (error: any) => {
      const errorMessage = error.response?.data?.message || 'Failed to update meal';
      toast.error(errorMessage);
    },
  });

  // Delete mutation
  const deleteMutation = useMutation({
    mutationFn: () => mealsApi.deleteMeal(id!),
    onSuccess: () => {
      toast.success('Meal deleted successfully');
      navigate('/meals');
    },
    onError: (error: any) => {
      const errorMessage = error.response?.data?.message || 'Failed to delete meal';
      toast.error(errorMessage);
    },
  });

  const handleEdit = () => {
    setEditedData({
      mealType: meal?.mealType,
      description: meal?.description,
      calories: meal?.calories,
      proteinG: meal?.proteinG,
      fatG: meal?.fatG,
      carbohydratesG: meal?.carbohydratesG,
      fiberG: meal?.fiberG,
      sugarG: meal?.sugarG,
      sodiumMg: meal?.sodiumMg,
    });
    setIsEditing(true);
  };

  const handleCancelEdit = () => {
    setIsEditing(false);
    setEditedData({});
  };

  const handleSave = () => {
    updateMutation.mutate(editedData as MealUpdateForm);
  };

  const handleDelete = () => {
    deleteMutation.mutate();
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <Loader2 className="w-8 h-8 animate-spin text-primary-600" />
      </div>
    );
  }

  if (isError || !meal) {
    return (
      <div className="text-center py-12">
        <p className="text-red-600 mb-4">Meal not found</p>
        <Link to="/meals" className="text-primary-600 hover:text-primary-700 font-semibold">
          Back to Meals
        </Link>
      </div>
    );
  }

  const confidence = meal.confidence !== undefined ? getConfidenceLevel(meal.confidence) : null;

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <Link
          to="/meals"
          className="flex items-center space-x-2 text-gray-600 hover:text-gray-900 transition"
        >
          <ArrowLeft className="w-5 h-5" />
          <span>Back to Meals</span>
        </Link>

        <div className="flex items-center space-x-3">
          {!isEditing ? (
            <>
              <button
                onClick={handleEdit}
                className="flex items-center space-x-2 px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition"
              >
                <Edit className="w-4 h-4" />
                <span>Edit</span>
              </button>
              <button
                onClick={() => setShowDeleteConfirm(true)}
                className="flex items-center space-x-2 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition"
              >
                <Trash2 className="w-4 h-4" />
                <span>Delete</span>
              </button>
            </>
          ) : (
            <>
              <button
                onClick={handleCancelEdit}
                className="flex items-center space-x-2 px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 transition"
              >
                <X className="w-4 h-4" />
                <span>Cancel</span>
              </button>
              <button
                onClick={handleSave}
                disabled={updateMutation.isPending}
                className="flex items-center space-x-2 px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700 transition disabled:opacity-50"
              >
                <Save className="w-4 h-4" />
                <span>{updateMutation.isPending ? 'Saving...' : 'Save'}</span>
              </button>
            </>
          )}
        </div>
      </div>

      {/* Status Banner */}
      <div
        className={`rounded-lg p-4 flex items-center space-x-3 ${
          meal.analysisStatus === 'COMPLETED'
            ? 'bg-green-50 border border-green-200'
            : meal.analysisStatus === 'PENDING'
            ? 'bg-yellow-50 border border-yellow-200'
            : 'bg-red-50 border border-red-200'
        }`}
      >
        {meal.analysisStatus === 'COMPLETED' ? (
          <CheckCircle className="w-6 h-6 text-green-600" />
        ) : meal.analysisStatus === 'PENDING' ? (
          <Clock className="w-6 h-6 text-yellow-600" />
        ) : (
          <AlertCircle className="w-6 h-6 text-red-600" />
        )}
        <div className="flex-1">
          <p
            className={`font-semibold ${
              meal.analysisStatus === 'COMPLETED'
                ? 'text-green-900'
                : meal.analysisStatus === 'PENDING'
                ? 'text-yellow-900'
                : 'text-red-900'
            }`}
          >
            Analysis Status: {meal.analysisStatus}
          </p>
          {confidence && (
            <p className="text-sm text-gray-600">
              AI Confidence: {confidence.text} ({Math.round((meal.confidence || 0) * 100)}%)
            </p>
          )}
        </div>
      </div>

      {/* Main Content */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Image */}
        {meal.imageUrl && (
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
            <img
              src={meal.imageUrl}
              alt={meal.description || 'Meal'}
              className="w-full h-96 object-cover"
            />
          </div>
        )}

        {/* Meal Info */}
        <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-200 space-y-6">
          <div>
            <h1 className="text-2xl font-bold text-gray-900 mb-3">Meal Details</h1>
            <div className="space-y-3">
              {/* Meal Type */}
              <div>
                <label className="block text-sm font-medium text-gray-600 mb-1">Meal Type</label>
                {isEditing ? (
                  <select
                    value={editedData.mealType}
                    onChange={(e) => setEditedData({ ...editedData, mealType: e.target.value as any })}
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
                  >
                    <option value="BREAKFAST">Breakfast</option>
                    <option value="LUNCH">Lunch</option>
                    <option value="DINNER">Dinner</option>
                    <option value="SNACK">Snack</option>
                  </select>
                ) : (
                  <span className={`inline-block px-3 py-1 text-sm font-semibold rounded ${getMealTypeColor(meal.mealType)}`}>
                    {getMealTypeName(meal.mealType)}
                  </span>
                )}
              </div>

              {/* Date/Time */}
              <div>
                <label className="block text-sm font-medium text-gray-600 mb-1">Date & Time</label>
                <p className="text-gray-900">{formatDateTime(meal.mealTime)}</p>
              </div>

              {/* Description */}
              <div>
                <label className="block text-sm font-medium text-gray-600 mb-1">Description</label>
                {isEditing ? (
                  <textarea
                    value={editedData.description || ''}
                    onChange={(e) => setEditedData({ ...editedData, description: e.target.value })}
                    rows={3}
                    className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
                  />
                ) : (
                  <p className="text-gray-900">{meal.description || 'No description'}</p>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Nutrition Information */}
      <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-200">
        <h2 className="text-xl font-bold text-gray-900 mb-6">Nutrition Information</h2>

        {/* Macronutrients */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
          <NutritionField
            label="Calories"
            value={isEditing ? editedData.calories : meal.calories}
            unit="kcal"
            isEditing={isEditing}
            onChange={(val) => setEditedData({ ...editedData, calories: val })}
            color="gray"
          />
          <NutritionField
            label="Protein"
            value={isEditing ? editedData.proteinG : meal.proteinG}
            unit="g"
            isEditing={isEditing}
            onChange={(val) => setEditedData({ ...editedData, proteinG: val })}
            color="red"
          />
          <NutritionField
            label="Fat"
            value={isEditing ? editedData.fatG : meal.fatG}
            unit="g"
            isEditing={isEditing}
            onChange={(val) => setEditedData({ ...editedData, fatG: val })}
            color="orange"
          />
          <NutritionField
            label="Carbs"
            value={isEditing ? editedData.carbohydratesG : meal.carbohydratesG}
            unit="g"
            isEditing={isEditing}
            onChange={(val) => setEditedData({ ...editedData, carbohydratesG: val })}
            color="blue"
          />
        </div>

        {/* Additional Nutrients */}
        <h3 className="text-lg font-semibold text-gray-900 mb-4">Additional Nutrients</h3>
        <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
          <NutritionField
            label="Fiber"
            value={isEditing ? editedData.fiberG : meal.fiberG}
            unit="g"
            isEditing={isEditing}
            onChange={(val) => setEditedData({ ...editedData, fiberG: val })}
            color="green"
          />
          <NutritionField
            label="Sugar"
            value={isEditing ? editedData.sugarG : meal.sugarG}
            unit="g"
            isEditing={isEditing}
            onChange={(val) => setEditedData({ ...editedData, sugarG: val })}
            color="pink"
          />
          <NutritionField
            label="Sodium"
            value={isEditing ? editedData.sodiumMg : meal.sodiumMg}
            unit="mg"
            isEditing={isEditing}
            onChange={(val) => setEditedData({ ...editedData, sodiumMg: val })}
            color="purple"
          />
        </div>
      </div>

      {/* Delete Confirmation Modal */}
      {showDeleteConfirm && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-xl shadow-xl p-6 max-w-md w-full">
            <h3 className="text-xl font-bold text-gray-900 mb-4">Delete Meal?</h3>
            <p className="text-gray-600 mb-6">
              Are you sure you want to delete this meal? This action cannot be undone.
            </p>
            <div className="flex items-center space-x-3">
              <button
                onClick={() => setShowDeleteConfirm(false)}
                className="flex-1 px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 transition"
              >
                Cancel
              </button>
              <button
                onClick={handleDelete}
                disabled={deleteMutation.isPending}
                className="flex-1 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition disabled:opacity-50"
              >
                {deleteMutation.isPending ? 'Deleting...' : 'Delete'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// Nutrition Field Component
function NutritionField({
  label,
  value,
  unit,
  isEditing,
  onChange,
  color,
}: {
  label: string;
  value?: number;
  unit: string;
  isEditing: boolean;
  onChange: (value: number | undefined) => void;
  color: string;
}) {
  const colorClasses = {
    gray: 'bg-gray-50 border-gray-200',
    red: 'bg-red-50 border-red-200',
    orange: 'bg-orange-50 border-orange-200',
    blue: 'bg-blue-50 border-blue-200',
    green: 'bg-green-50 border-green-200',
    pink: 'bg-pink-50 border-pink-200',
    purple: 'bg-purple-50 border-purple-200',
  };

  return (
    <div className={`p-4 rounded-lg border ${colorClasses[color as keyof typeof colorClasses]}`}>
      <label className="block text-sm font-medium text-gray-600 mb-2">{label}</label>
      {isEditing ? (
        <input
          type="number"
          value={value || ''}
          onChange={(e) => onChange(e.target.value ? parseFloat(e.target.value) : undefined)}
          className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
          placeholder="0"
          min="0"
          step="0.1"
        />
      ) : (
        <p className="text-2xl font-bold text-gray-900">
          {value !== undefined ? (unit === 'kcal' ? formatCalories(value) : formatMacro(value, unit)) : '—'}
        </p>
      )}
    </div>
  );
}
