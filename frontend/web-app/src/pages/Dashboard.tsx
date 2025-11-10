import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { mealsApi, goalsApi } from '@/lib/api';
import { MealResponse, GoalResponse } from '@/types';
import { formatDate, formatCalories, formatMacro, calculatePercentage, getMealTypeName, getMealTypeColor } from '@/lib/utils';
import { PieChart, Pie, Cell, ResponsiveContainer, Legend, Tooltip } from 'recharts';
import { TrendingUp, Target, Calendar, Plus, Loader2 } from 'lucide-react';

export default function Dashboard() {
  // Get today's date range
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const tomorrow = new Date(today);
  tomorrow.setDate(tomorrow.getDate() + 1);

  // Fetch user's goals
  const { data: goals, isLoading: goalsLoading } = useQuery({
    queryKey: ['goals'],
    queryFn: () => goalsApi.getGoals(),
  });

  // Fetch today's meals
  const { data: todayMeals, isLoading: mealsLoading } = useQuery({
    queryKey: ['meals', 'today'],
    queryFn: () => mealsApi.getMealsByDateRange(today.toISOString(), tomorrow.toISOString()),
  });

  // Fetch recent meals
  const { data: recentMeals, isLoading: recentLoading } = useQuery({
    queryKey: ['meals', 'recent'],
    queryFn: () => mealsApi.getMealsPaginated(0, 5),
  });

  // Calculate today's totals
  const todayTotals = todayMeals?.reduce(
    (acc, meal) => ({
      calories: acc.calories + (meal.calories || 0),
      protein: acc.protein + (meal.proteinG || 0),
      fat: acc.fat + (meal.fatG || 0),
      carbs: acc.carbs + (meal.carbohydratesG || 0),
    }),
    { calories: 0, protein: 0, fat: 0, carbs: 0 }
  ) || { calories: 0, protein: 0, fat: 0, carbs: 0 };

  // Prepare data for macro pie chart
  const macroData = [
    { name: 'Protein', value: todayTotals.protein, color: '#ef4444' },
    { name: 'Fat', value: todayTotals.fat, color: '#f59e0b' },
    { name: 'Carbs', value: todayTotals.carbs, color: '#3b82f6' },
  ];

  const isLoading = goalsLoading || mealsLoading || recentLoading;

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <Loader2 className="w-8 h-8 animate-spin text-primary-600" />
      </div>
    );
  }

  return (
    <div className="space-y-8">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-900">Dashboard</h1>
          <p className="text-gray-600 mt-1 flex items-center">
            <Calendar className="w-4 h-4 mr-2" />
            {formatDate(today)}
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

      {/* Stats Overview */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        <StatCard
          title="Calories"
          current={todayTotals.calories}
          target={goals?.dailyCalories || 2000}
          unit="kcal"
          icon={TrendingUp}
          color="primary"
        />
        <StatCard
          title="Protein"
          current={todayTotals.protein}
          target={goals?.dailyProteinG || 150}
          unit="g"
          icon={Target}
          color="red"
        />
        <StatCard
          title="Fat"
          current={todayTotals.fat}
          target={goals?.dailyFatG || 65}
          unit="g"
          icon={Target}
          color="orange"
        />
        <StatCard
          title="Carbs"
          current={todayTotals.carbs}
          target={goals?.dailyCarbsG || 250}
          unit="g"
          icon={Target}
          color="blue"
        />
      </div>

      {/* Main Content Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        {/* Daily Progress */}
        <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-200">
          <h2 className="text-xl font-bold text-gray-900 mb-6">Today's Progress</h2>
          <div className="space-y-6">
            <ProgressBar
              label="Calories"
              current={todayTotals.calories}
              target={goals?.dailyCalories || 2000}
              unit="kcal"
              color="bg-primary-600"
            />
            <ProgressBar
              label="Protein"
              current={todayTotals.protein}
              target={goals?.dailyProteinG || 150}
              unit="g"
              color="bg-red-500"
            />
            <ProgressBar
              label="Fat"
              current={todayTotals.fat}
              target={goals?.dailyFatG || 65}
              unit="g"
              color="bg-orange-500"
            />
            <ProgressBar
              label="Carbs"
              current={todayTotals.carbs}
              target={goals?.dailyCarbsG || 250}
              unit="g"
              color="bg-blue-500"
            />
          </div>
        </div>

        {/* Macro Breakdown Chart */}
        <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-200">
          <h2 className="text-xl font-bold text-gray-900 mb-6">Macro Breakdown</h2>
          {todayTotals.protein + todayTotals.fat + todayTotals.carbs > 0 ? (
            <ResponsiveContainer width="100%" height={250}>
              <PieChart>
                <Pie
                  data={macroData}
                  cx="50%"
                  cy="50%"
                  labelLine={false}
                  label={renderCustomLabel}
                  outerRadius={80}
                  fill="#8884d8"
                  dataKey="value"
                >
                  {macroData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip formatter={(value) => `${formatMacro(Number(value))}`} />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <div className="flex items-center justify-center h-64 text-gray-400">
              <p>No meals logged yet today</p>
            </div>
          )}
        </div>
      </div>

      {/* Recent Meals */}
      <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-200">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-xl font-bold text-gray-900">Recent Meals</h2>
          <Link
            to="/meals"
            className="text-primary-600 hover:text-primary-700 font-semibold text-sm"
          >
            View All
          </Link>
        </div>
        {recentMeals && recentMeals.content.length > 0 ? (
          <div className="space-y-4">
            {recentMeals.content.map((meal) => (
              <MealCard key={meal.id} meal={meal} />
            ))}
          </div>
        ) : (
          <div className="text-center py-12 text-gray-400">
            <p>No meals logged yet</p>
            <Link
              to="/meals/upload"
              className="inline-block mt-4 text-primary-600 hover:text-primary-700 font-semibold"
            >
              Log your first meal
            </Link>
          </div>
        )}
      </div>
    </div>
  );
}

// Stat Card Component
function StatCard({
  title,
  current,
  target,
  unit,
  icon: Icon,
  color,
}: {
  title: string;
  current: number;
  target: number;
  unit: string;
  icon: any;
  color: string;
}) {
  const percentage = calculatePercentage(current, target);
  const colorClasses = {
    primary: 'bg-primary-50 text-primary-600',
    red: 'bg-red-50 text-red-600',
    orange: 'bg-orange-50 text-orange-600',
    blue: 'bg-blue-50 text-blue-600',
  };

  return (
    <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-200">
      <div className="flex items-center justify-between mb-4">
        <span className="text-sm font-medium text-gray-600">{title}</span>
        <div className={`p-2 rounded-lg ${colorClasses[color as keyof typeof colorClasses]}`}>
          <Icon className="w-5 h-5" />
        </div>
      </div>
      <div className="space-y-2">
        <div className="flex items-baseline space-x-2">
          <span className="text-3xl font-bold text-gray-900">
            {Math.round(current)}
          </span>
          <span className="text-gray-600">/ {target}{unit}</span>
        </div>
        <div className="flex items-center space-x-2">
          <div className="flex-1 bg-gray-200 rounded-full h-2">
            <div
              className={`h-2 rounded-full ${
                percentage > 100 ? 'bg-red-500' : 'bg-primary-600'
              }`}
              style={{ width: `${Math.min(percentage, 100)}%` }}
            />
          </div>
          <span className="text-sm font-semibold text-gray-700">{percentage}%</span>
        </div>
      </div>
    </div>
  );
}

// Progress Bar Component
function ProgressBar({
  label,
  current,
  target,
  unit,
  color,
}: {
  label: string;
  current: number;
  target: number;
  unit: string;
  color: string;
}) {
  const percentage = calculatePercentage(current, target);

  return (
    <div>
      <div className="flex items-center justify-between mb-2">
        <span className="text-sm font-medium text-gray-700">{label}</span>
        <span className="text-sm text-gray-600">
          {Math.round(current)} / {target}{unit}
        </span>
      </div>
      <div className="flex items-center space-x-3">
        <div className="flex-1 bg-gray-200 rounded-full h-3">
          <div
            className={`h-3 rounded-full transition-all ${
              percentage > 100 ? 'bg-red-500' : color
            }`}
            style={{ width: `${Math.min(percentage, 100)}%` }}
          />
        </div>
        <span className="text-sm font-semibold text-gray-700 w-12 text-right">
          {percentage}%
        </span>
      </div>
    </div>
  );
}

// Meal Card Component
function MealCard({ meal }: { meal: MealResponse }) {
  return (
    <Link
      to={`/meals/${meal.id}`}
      className="flex items-center justify-between p-4 border border-gray-200 rounded-lg hover:bg-gray-50 transition"
    >
      <div className="flex items-center space-x-4">
        {meal.imageUrl && (
          <img
            src={meal.imageUrl}
            alt={meal.description || 'Meal'}
            className="w-16 h-16 rounded-lg object-cover"
          />
        )}
        <div>
          <div className="flex items-center space-x-2 mb-1">
            <span className={`px-2 py-1 text-xs font-semibold rounded ${getMealTypeColor(meal.mealType)}`}>
              {getMealTypeName(meal.mealType)}
            </span>
            <span className="text-sm text-gray-600">
              {new Date(meal.mealTime).toLocaleTimeString('en-US', {
                hour: 'numeric',
                minute: '2-digit',
              })}
            </span>
          </div>
          <p className="text-sm font-medium text-gray-900">
            {meal.description || 'No description'}
          </p>
        </div>
      </div>
      <div className="text-right">
        <p className="text-lg font-bold text-gray-900">
          {meal.calories ? formatCalories(meal.calories) : '—'}
        </p>
        <p className="text-xs text-gray-600">calories</p>
      </div>
    </Link>
  );
}

// Custom label for pie chart
const renderCustomLabel = ({
  cx,
  cy,
  midAngle,
  innerRadius,
  outerRadius,
  percent,
}: any) => {
  const radius = innerRadius + (outerRadius - innerRadius) * 0.5;
  const x = cx + radius * Math.cos(-midAngle * (Math.PI / 180));
  const y = cy + radius * Math.sin(-midAngle * (Math.PI / 180));

  return (
    <text
      x={x}
      y={y}
      fill="white"
      textAnchor={x > cx ? 'start' : 'end'}
      dominantBaseline="central"
      className="font-semibold"
    >
      {`${(percent * 100).toFixed(0)}%`}
    </text>
  );
};
