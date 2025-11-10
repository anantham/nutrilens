import { useState, FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDropzone } from 'react-dropzone';
import { mealsApi } from '@/lib/api';
import { MealUploadForm } from '@/types';
import { isValidImageFile, formatFileSize } from '@/lib/utils';
import toast from 'react-hot-toast';
import { Upload, X, Image as ImageIcon, Calendar, Clock, Loader2 } from 'lucide-react';

export default function MealUpload() {
  const navigate = useNavigate();
  const [isLoading, setIsLoading] = useState(false);
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [formData, setFormData] = useState<Omit<MealUploadForm, 'image'>>({
    mealType: 'LUNCH',
    mealTime: new Date().toISOString().slice(0, 16), // Format for datetime-local input
    description: '',
  });

  // React Dropzone configuration
  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    accept: {
      'image/jpeg': ['.jpg', '.jpeg'],
      'image/png': ['.png'],
      'image/heic': ['.heic'],
      'image/webp': ['.webp'],
    },
    maxFiles: 1,
    maxSize: 10 * 1024 * 1024, // 10MB
    onDrop: (acceptedFiles) => {
      if (acceptedFiles.length > 0) {
        const file = acceptedFiles[0];
        if (!isValidImageFile(file)) {
          toast.error('Invalid file type. Please upload a JPEG, PNG, HEIC, or WebP image.');
          return;
        }
        setImageFile(file);
        setImagePreview(URL.createObjectURL(file));
      }
    },
    onDropRejected: (fileRejections) => {
      fileRejections.forEach((rejection) => {
        rejection.errors.forEach((error) => {
          if (error.code === 'file-too-large') {
            toast.error('File is too large. Maximum size is 10MB.');
          } else if (error.code === 'file-invalid-type') {
            toast.error('Invalid file type. Please upload a JPEG, PNG, HEIC, or WebP image.');
          } else {
            toast.error(error.message);
          }
        });
      });
    },
  });

  const handleRemoveImage = () => {
    setImageFile(null);
    if (imagePreview) {
      URL.revokeObjectURL(imagePreview);
      setImagePreview(null);
    }
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();

    if (!imageFile) {
      toast.error('Please upload an image of your meal.');
      return;
    }

    setIsLoading(true);
    try {
      const uploadData: MealUploadForm = {
        ...formData,
        image: imageFile,
        mealTime: new Date(formData.mealTime).toISOString(),
      };

      const result = await mealsApi.uploadMeal(uploadData);
      toast.success('Meal uploaded successfully! AI analysis in progress...');
      navigate(`/meals/${result.id}`);
    } catch (error: any) {
      console.error('Upload error:', error);
      const errorMessage = error.response?.data?.message || 'Failed to upload meal. Please try again.';
      toast.error(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="max-w-3xl mx-auto">
      {/* Header */}
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-gray-900">Log a New Meal</h1>
        <p className="text-gray-600 mt-2">
          Upload a photo of your meal and let AI analyze the nutrition
        </p>
      </div>

      <form onSubmit={handleSubmit} className="space-y-8">
        {/* Image Upload */}
        <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-200">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Meal Photo</h2>

          {!imagePreview ? (
            <div
              {...getRootProps()}
              className={`border-2 border-dashed rounded-lg p-12 text-center cursor-pointer transition ${
                isDragActive
                  ? 'border-primary-500 bg-primary-50'
                  : 'border-gray-300 hover:border-primary-400 hover:bg-gray-50'
              }`}
            >
              <input {...getInputProps()} />
              <div className="flex flex-col items-center space-y-4">
                <div className="w-16 h-16 bg-primary-100 rounded-full flex items-center justify-center">
                  {isDragActive ? (
                    <Upload className="w-8 h-8 text-primary-600 animate-bounce" />
                  ) : (
                    <ImageIcon className="w-8 h-8 text-primary-600" />
                  )}
                </div>
                <div>
                  <p className="text-lg font-medium text-gray-900">
                    {isDragActive ? 'Drop your image here' : 'Drag & drop your meal photo'}
                  </p>
                  <p className="text-sm text-gray-500 mt-1">
                    or click to browse (JPEG, PNG, HEIC, WebP, max 10MB)
                  </p>
                </div>
              </div>
            </div>
          ) : (
            <div className="relative">
              <img
                src={imagePreview}
                alt="Meal preview"
                className="w-full h-96 object-cover rounded-lg"
              />
              <button
                type="button"
                onClick={handleRemoveImage}
                className="absolute top-4 right-4 p-2 bg-red-500 text-white rounded-full hover:bg-red-600 transition shadow-lg"
              >
                <X className="w-5 h-5" />
              </button>
              {imageFile && (
                <div className="mt-3 text-sm text-gray-600">
                  <p className="font-medium">{imageFile.name}</p>
                  <p className="text-gray-500">{formatFileSize(imageFile.size)}</p>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Meal Details */}
        <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-200">
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Meal Details</h2>

          <div className="space-y-4">
            {/* Meal Type */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Meal Type
              </label>
              <select
                value={formData.mealType}
                onChange={(e) => setFormData({ ...formData, mealType: e.target.value as any })}
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              >
                <option value="BREAKFAST">Breakfast</option>
                <option value="LUNCH">Lunch</option>
                <option value="DINNER">Dinner</option>
                <option value="SNACK">Snack</option>
              </select>
            </div>

            {/* Meal Time */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2 flex items-center">
                <Calendar className="w-4 h-4 mr-2" />
                Date & Time
              </label>
              <input
                type="datetime-local"
                value={formData.mealTime}
                onChange={(e) => setFormData({ ...formData, mealTime: e.target.value })}
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
              />
            </div>

            {/* Description (Optional) */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Description (Optional)
              </label>
              <textarea
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                rows={3}
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                placeholder="Add notes about your meal..."
              />
            </div>
          </div>
        </div>

        {/* Manual Nutrition Input (Optional) */}
        <div className="bg-white rounded-xl shadow-sm p-6 border border-gray-200">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-gray-900">
              Manual Nutrition (Optional)
            </h2>
            <span className="text-xs text-gray-500 bg-gray-100 px-3 py-1 rounded-full">
              Leave blank for AI analysis
            </span>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Calories (kcal)
              </label>
              <input
                type="number"
                value={formData.calories || ''}
                onChange={(e) => setFormData({ ...formData, calories: e.target.value ? parseFloat(e.target.value) : undefined })}
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                placeholder="0"
                min="0"
                step="1"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Protein (g)
              </label>
              <input
                type="number"
                value={formData.proteinG || ''}
                onChange={(e) => setFormData({ ...formData, proteinG: e.target.value ? parseFloat(e.target.value) : undefined })}
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                placeholder="0"
                min="0"
                step="0.1"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Fat (g)
              </label>
              <input
                type="number"
                value={formData.fatG || ''}
                onChange={(e) => setFormData({ ...formData, fatG: e.target.value ? parseFloat(e.target.value) : undefined })}
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                placeholder="0"
                min="0"
                step="0.1"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                Carbs (g)
              </label>
              <input
                type="number"
                value={formData.carbohydratesG || ''}
                onChange={(e) => setFormData({ ...formData, carbohydratesG: e.target.value ? parseFloat(e.target.value) : undefined })}
                className="w-full px-4 py-3 border border-gray-300 rounded-lg focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                placeholder="0"
                min="0"
                step="0.1"
              />
            </div>
          </div>

          <p className="text-sm text-gray-500 mt-4">
            If you leave these fields blank, our AI will automatically analyze the image and estimate the nutrition values.
          </p>
        </div>

        {/* Submit Button */}
        <div className="flex items-center space-x-4">
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="flex-1 px-6 py-3 border border-gray-300 text-gray-700 font-semibold rounded-lg hover:bg-gray-50 transition"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={isLoading || !imageFile}
            className="flex-1 px-6 py-3 bg-primary-600 text-white font-semibold rounded-lg hover:bg-primary-700 transition disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isLoading ? (
              <span className="flex items-center justify-center">
                <Loader2 className="w-5 h-5 mr-2 animate-spin" />
                Uploading...
              </span>
            ) : (
              <span className="flex items-center justify-center">
                <Upload className="w-5 h-5 mr-2" />
                Upload Meal
              </span>
            )}
          </button>
        </div>
      </form>
    </div>
  );
}
