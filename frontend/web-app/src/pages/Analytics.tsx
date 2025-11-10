import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { analyticsApi } from '@/lib/api';
import {
  BarChart,
  Bar,
  LineChart,
  Line,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import { TrendingUp, Award, Target, AlertCircle, Loader2 } from 'lucide-react';

export default function Analytics() {
  const [dateRange, setDateRange] = useState<{ start: string; end: string }>({
    start: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
    end: new Date().toISOString().split('T')[0],
  });

  // Fetch AI accuracy metrics
  const { data: accuracyData, isLoading: accuracyLoading } = useQuery({
    queryKey: ['analytics', 'accuracy', dateRange],
    queryFn: () => {
      if (dateRange.start && dateRange.end) {
        const start = new Date(dateRange.start).toISOString();
        const end = new Date(dateRange.end).toISOString();
        return analyticsApi.getAccuracyByDateRange(start, end);
      }
      return analyticsApi.getAccuracyMetrics();
    },
  });

  // Fetch field-specific accuracy
  const { data: fieldAccuracy, isLoading: fieldLoading } = useQuery({
    queryKey: ['analytics', 'fields'],
    queryFn: () => analyticsApi.getFieldAccuracy(),
  });

  // Fetch confidence distribution
  const { data: confidenceData, isLoading: confidenceLoading } = useQuery({
    queryKey: ['analytics', 'confidence'],
    queryFn: () => analyticsApi.getConfidenceDistribution(),
  });

  const isLoading = accuracyLoading || fieldLoading || confidenceLoading;

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <Loader2 className="w-8 h-8 animate-spin text-primary-600" />
      </div>
    );
  }

  // Prepare data for charts
  const fieldAccuracyChart = fieldAccuracy?.map((item) => ({
    field: item.fieldName.replace('_', ' '),
    accuracy: (1 - item.avgPercentError / 100) * 100,
    corrections: item.correctionCount,
  })) || [];

  const confidenceDistChart = [
    { name: 'High (≥80%)', value: confidenceData?.high || 0, color: '#22c55e' },
    { name: 'Medium (60-80%)', value: confidenceData?.medium || 0, color: '#f59e0b' },
    { name: 'Low (<60%)', value: confidenceData?.low || 0, color: '#ef4444' },
  ];

  // Calculate overall stats
  const totalCorrections = fieldAccuracyChart.reduce((sum, item) => sum + item.corrections, 0);
  const avgAccuracy = fieldAccuracyChart.length > 0
    ? fieldAccuracyChart.reduce((sum, item) => sum + item.accuracy, 0) / fieldAccuracyChart.length
    : 0;
  const totalPredictions = (confidenceData?.high || 0) + (confidenceData?.medium || 0) + (confidenceData?.low || 0);

  return (
    <div className="space-y-8">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-bold text-gray-900">AI Analytics</h1>
        <p className="text-gray-600 mt-2">
          Track AI accuracy and correction patterns over time
        </p>
      </div>

      {/* Date Range Filter */}
      <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-200">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              From Date
            </label>
            <input
              type="date"
              value={dateRange.start}
              onChange={(e) => setDateRange({ ...dateRange, start: e.target.value })}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              To Date
            </label>
            <input
              type="date"
              value={dateRange.end}
              onChange={(e) => setDateRange({ ...dateRange, end: e.target.value })}
              className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500"
            />
          </div>
        </div>
      </div>

      {/* Stats Overview */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        <StatCard
          title="Average Accuracy"
          value={`${avgAccuracy.toFixed(1)}%`}
          icon={Target}
          color="primary"
          trend="+2.5%"
        />
        <StatCard
          title="Total Predictions"
          value={totalPredictions.toString()}
          icon={TrendingUp}
          color="blue"
        />
        <StatCard
          title="Total Corrections"
          value={totalCorrections.toString()}
          icon={AlertCircle}
          color="orange"
        />
        <StatCard
          title="High Confidence"
          value={`${Math.round((confidenceData?.high || 0) / Math.max(totalPredictions, 1) * 100)}%`}
          icon={Award}
          color="green"
        />
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        {/* Field Accuracy Chart */}
        <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-200">
          <h2 className="text-xl font-bold text-gray-900 mb-6">Accuracy by Field</h2>
          {fieldAccuracyChart.length > 0 ? (
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={fieldAccuracyChart}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="field" angle={-45} textAnchor="end" height={100} />
                <YAxis domain={[0, 100]} />
                <Tooltip formatter={(value) => `${Number(value).toFixed(1)}%`} />
                <Legend />
                <Bar dataKey="accuracy" fill="#3b82f6" name="Accuracy (%)" />
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <div className="flex items-center justify-center h-64 text-gray-400">
              <p>No accuracy data available</p>
            </div>
          )}
        </div>

        {/* Confidence Distribution */}
        <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-200">
          <h2 className="text-xl font-bold text-gray-900 mb-6">Confidence Distribution</h2>
          {totalPredictions > 0 ? (
            <ResponsiveContainer width="100%" height={300}>
              <PieChart>
                <Pie
                  data={confidenceDistChart}
                  cx="50%"
                  cy="50%"
                  labelLine={false}
                  label={renderPieLabel}
                  outerRadius={100}
                  fill="#8884d8"
                  dataKey="value"
                >
                  {confidenceDistChart.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <div className="flex items-center justify-center h-64 text-gray-400">
              <p>No predictions available</p>
            </div>
          )}
        </div>
      </div>

      {/* Correction Details */}
      <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-200">
        <h2 className="text-xl font-bold text-gray-900 mb-6">Correction Details by Field</h2>
        {fieldAccuracyChart.length > 0 ? (
          <ResponsiveContainer width="100%" height={300}>
            <BarChart data={fieldAccuracyChart}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="field" angle={-45} textAnchor="end" height={100} />
              <YAxis />
              <Tooltip />
              <Legend />
              <Bar dataKey="corrections" fill="#f59e0b" name="Correction Count" />
            </BarChart>
          </ResponsiveContainer>
        ) : (
          <div className="flex items-center justify-center h-64 text-gray-400">
            <p>No correction data available</p>
          </div>
        )}
      </div>

      {/* Field Accuracy Table */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        <div className="p-6">
          <h2 className="text-xl font-bold text-gray-900 mb-6">Detailed Field Statistics</h2>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Field
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Accuracy
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Avg Error
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Corrections
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Avg Confidence
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {fieldAccuracy?.map((field, index) => (
                <tr key={index} className="hover:bg-gray-50">
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                    {field.fieldName.replace('_', ' ')}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    <span
                      className={`px-2 py-1 rounded-full font-semibold ${
                        (1 - field.avgPercentError / 100) * 100 >= 90
                          ? 'bg-green-100 text-green-800'
                          : (1 - field.avgPercentError / 100) * 100 >= 70
                          ? 'bg-yellow-100 text-yellow-800'
                          : 'bg-red-100 text-red-800'
                      }`}
                    >
                      {((1 - field.avgPercentError / 100) * 100).toFixed(1)}%
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {field.avgPercentError.toFixed(1)}%
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {field.correctionCount}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {(field.avgConfidence * 100).toFixed(1)}%
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Insights */}
      <div className="bg-gradient-to-r from-primary-50 to-blue-50 rounded-xl p-6 border border-primary-200">
        <h3 className="text-lg font-bold text-gray-900 mb-3 flex items-center">
          <Award className="w-5 h-5 mr-2 text-primary-600" />
          AI Performance Insights
        </h3>
        <ul className="space-y-2 text-sm text-gray-700">
          <li className="flex items-start">
            <span className="text-primary-600 mr-2">•</span>
            <span>
              The AI has made <strong>{totalPredictions}</strong> predictions with an average accuracy of{' '}
              <strong>{avgAccuracy.toFixed(1)}%</strong>.
            </span>
          </li>
          <li className="flex items-start">
            <span className="text-primary-600 mr-2">•</span>
            <span>
              <strong>{Math.round((confidenceData?.high || 0) / Math.max(totalPredictions, 1) * 100)}%</strong> of predictions had high confidence (≥80%).
            </span>
          </li>
          <li className="flex items-start">
            <span className="text-primary-600 mr-2">•</span>
            <span>
              Users have made <strong>{totalCorrections}</strong> corrections to improve AI accuracy.
            </span>
          </li>
          {fieldAccuracyChart.length > 0 && (
            <li className="flex items-start">
              <span className="text-primary-600 mr-2">•</span>
              <span>
                Most accurate field:{' '}
                <strong>
                  {fieldAccuracyChart.reduce((max, item) =>
                    item.accuracy > max.accuracy ? item : max
                  ).field}
                </strong>{' '}
                at{' '}
                <strong>
                  {fieldAccuracyChart.reduce((max, item) =>
                    item.accuracy > max.accuracy ? item : max
                  ).accuracy.toFixed(1)}%
                </strong>
              </span>
            </li>
          )}
        </ul>
      </div>
    </div>
  );
}

// Stat Card Component
function StatCard({
  title,
  value,
  icon: Icon,
  color,
  trend,
}: {
  title: string;
  value: string;
  icon: any;
  color: string;
  trend?: string;
}) {
  const colorClasses = {
    primary: 'bg-primary-50 text-primary-600',
    blue: 'bg-blue-50 text-blue-600',
    orange: 'bg-orange-50 text-orange-600',
    green: 'bg-green-50 text-green-600',
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
        <p className="text-3xl font-bold text-gray-900">{value}</p>
        {trend && (
          <p className="text-sm text-green-600 font-semibold">{trend} from last month</p>
        )}
      </div>
    </div>
  );
}

// Custom label for pie chart
const renderPieLabel = ({
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
