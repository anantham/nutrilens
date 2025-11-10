import { useState } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { mealsApi } from '@/lib/api';
import { MealResponse } from '@/types';
import {
  formatDateTime,
  formatCalories,
  formatMacro,
  getMealTypeName,
  getMealTypeColor,
  getConfidenceLevel,
} from '@/lib/utils';
import { Filter, Plus, Loader2, ChevronDown } from 'lucide-react';

type MealTypeFilter = 'ALL' | 'BREAKFAST' | 'LUNCH' | 'DINNER' | 'SNACK';

export default function MealList() {
  const [mealTypeFilter, setMealTypeFilter] = useState<MealTypeFilter>('ALL');
  const [dateRange, setDateRange] = useState<{ start: string; end: string }>({
    start: '',
    end: '',
  });

  // Infinite query for paginated meals
  const {
    data,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    isLoading,
    isError,
    error,
  } = useInfiniteQuery({
    queryKey: ['meals', 'infinite', mealTypeFilter, dateRange],
    queryFn: ({ pageParam = 0 }) => {
      // Apply filters
      if (dateRange.start && dateRange.end) {
        const start = new Date(dateRange.start).toISOString();
        const end = new Date(dateRange.end).toISOString();
        return mealsApi.getMealsByDateRange(start, end);
      } else if (mealTypeFilter !== 'ALL') {
        return mealsApi.getMealsByType(mealTypeFilter);
      } else {
        return mealsApi.getMealsPaginated(pageParam, 20);
      }
    },
    getNextPageParam: (lastPage, pages) => {
      // Check if there are more pages
      if (lastPage.hasNext) {
        return pages.length; // Return next page number
      }
      return undefined;
    },
    initialPageParam: 0,
  });

  const allMeals = data?.pages.flatMap((page) => page.content) || [];

  const handleClearFilters = () => {
    setMealTypeFilter('ALL');
    setDateRange({ start: '', end: '' });
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <Loader2 className="w-8 h-8 animate-spin text-primary-600" />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="text-center py-12">
        <p className="text-red-600">Error loading meals: {error.message}</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Meal History</h1>
          <p className="text-gray-600 mt-1">
            View and manage all your logged meals
          </p>
        </div>
        <Link
          to="/meals/upload"
          className="flex items-center space-x-2 bg-primary-600 text-white px-6 py-3 rounded-lg hover:bg-primary-700 transition font-semibold"
        >
          <Plus className="w-5 h-5" />
          <span>Log Meal</span>
        </Link>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-200">
        <div className="flex items-center space-x-2 mb-4">
          <Filter className="w-5 h-5 text-gray-600" />
          <h2 className="text-lg font-semibold text-gray-900">Filters</h2>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {/* Meal Type Filter */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Meal Type
            </label>
            <select
              value={mealTypeFilter}
              onChange={(e) => setMealTypeFilter(e.target.value as MealTypeFilter)}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            >
              <option value="ALL">All Types</option>
              <option value="BREAKFAST">Breakfast</option>
              <option value="LUNCH">Lunch</option>
              <option value="DINNER">Dinner</option>
              <option value="SNACK">Snack</option>
            </select>
          </div>

          {/* Date Range Start */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              From Date
            </label>
            <input
              type="date"
              value={dateRange.start}
              onChange={(e) => setDateRange({ ...dateRange, start: e.target.value })}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            />
          </div>

          {/* Date Range End */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              To Date
            </label>
            <input
              type="date"
              value={dateRange.end}
              onChange={(e) => setDateRange({ ...dateRange, end: e.target.value })}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            />
          </div>
        </div>

        {/* Clear Filters */}
        {(mealTypeFilter !== 'ALL' || dateRange.start || dateRange.end) && (
          <button
            onClick={handleClearFilters}
            className="mt-4 text-sm text-primary-600 hover:text-primary-700 font-semibold"
          >
            Clear Filters
          </button>
        )}
      </div>

      {/* Meal List */}
      {allMeals.length > 0 ? (
        <div className="space-y-4">
          {allMeals.map((meal) => (
            <MealCard key={meal.id} meal={meal} />
          ))}
        </div>
      ) : (
        <div className="bg-white rounded-xl shadow-sm p-12 border border-gray-200 text-center">
          <p className="text-gray-400 mb-4">No meals found</p>
          <Link
            to="/meals/upload"
            className="inline-flex items-center space-x-2 text-primary-600 hover:text-primary-700 font-semibold"
          >
            <Plus className="w-5 h-5" />
            <span>Log your first meal</span>
          </Link>
        </div>
      )}

      {/* Load More Button */}
      {hasNextPage && (
        <div className="flex justify-center">
          <button
            onClick={() => fetchNextPage()}
            disabled={isFetchingNextPage}
            className="flex items-center space-x-2 px-6 py-3 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition disabled:opacity-50 font-semibold text-gray-700"
          >
            {isFetchingNextPage ? (
              <>
                <Loader2 className="w-5 h-5 animate-spin" />
                <span>Loading...</span>
              </>
            ) : (
              <>
                <ChevronDown className="w-5 h-5" />
                <span>Load More</span>
              </>
            )}
          </button>
        </div>
      )}
    </div>
  );
}

// Meal Card Component
function MealCard({ meal }: { meal: MealResponse }) {
  const confidence = getConfidenceLevel(meal.confidence || 0);

  return (
    <Link
      to={`/meals/${meal.id}`}
      className="block bg-white rounded-xl shadow-sm border border-gray-200 hover:shadow-md transition overflow-hidden"
    >
      <div className="flex flex-col md:flex-row">
        {/* Image */}
        {meal.imageUrl && (
          <div className="md:w-48 h-48 flex-shrink-0">
            <img
              src={meal.imageUrl}
              alt={meal.description || 'Meal'}
              className="w-full h-full object-cover"
            />
          </div>
        )}

        {/* Content */}
        <div className="flex-1 p-6">
          {/* Header */}
          <div className="flex items-start justify-between mb-3">
            <div className="flex-1">
              <div className="flex items-center space-x-2 mb-2">
                <span className={`px-3 py-1 text-xs font-semibold rounded-full ${getMealTypeColor(meal.mealType)}`}>
                  {getMealTypeName(meal.mealType)}
                </span>
                {meal.confidence !== undefined && (
                  <span className={`px-3 py-1 text-xs font-semibold rounded-full ${confidence.color} bg-opacity-10`}>
                    {confidence.text} Confidence
                  </span>
                )}
                <span
                  className={`px-3 py-1 text-xs font-semibold rounded-full ${
                    meal.analysisStatus === 'COMPLETED'
                      ? 'bg-green-100 text-green-800'
                      : meal.analysisStatus === 'PENDING'
                      ? 'bg-yellow-100 text-yellow-800'
                      : 'bg-red-100 text-red-800'
                  }`}
                >
                  {meal.analysisStatus}
                </span>
              </div>
              <h3 className="text-lg font-semibold text-gray-900 mb-1">
                {meal.description || 'No description'}
              </h3>
              <p className="text-sm text-gray-600">
                {formatDateTime(meal.mealTime)}
              </p>
            </div>
          </div>

          {/* Nutrition Info */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mt-4">
            <div className="text-center p-3 bg-gray-50 rounded-lg">
              <p className="text-2xl font-bold text-gray-900">
                {meal.calories ? formatCalories(meal.calories) : '—'}
              </p>
              <p className="text-xs text-gray-600 mt-1">Calories</p>
            </div>
            <div className="text-center p-3 bg-red-50 rounded-lg">
              <p className="text-2xl font-bold text-red-600">
                {meal.proteinG !== undefined ? formatMacro(meal.proteinG) : '—'}
              </p>
              <p className="text-xs text-gray-600 mt-1">Protein</p>
            </div>
            <div className="text-center p-3 bg-orange-50 rounded-lg">
              <p className="text-2xl font-bold text-orange-600">
                {meal.fatG !== undefined ? formatMacro(meal.fatG) : '—'}
              </p>
              <p className="text-xs text-gray-600 mt-1">Fat</p>
            </div>
            <div className="text-center p-3 bg-blue-50 rounded-lg">
              <p className="text-2xl font-bold text-blue-600">
                {meal.carbohydratesG !== undefined ? formatMacro(meal.carbohydratesG) : '—'}
              </p>
              <p className="text-xs text-gray-600 mt-1">Carbs</p>
            </div>
          </div>
        </div>
      </div>
    </Link>
  );
}
