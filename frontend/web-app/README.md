# Nutritheous Web Application

Modern React web application for the Nutritheous AI-powered nutrition tracking platform.

## Tech Stack

- **Framework**: React 18 with TypeScript
- **Build Tool**: Vite
- **Styling**: TailwindCSS
- **State Management**: React Query (@tanstack/react-query)
- **Routing**: React Router v6
- **HTTP Client**: Axios
- **Charts**: Recharts
- **UI Components**: Lucide React icons
- **File Upload**: React Dropzone
- **Notifications**: React Hot Toast

## Features

### Authentication
- User registration with profile information
- Login with JWT token management
- Protected routes
- Automatic token refresh handling

### Meal Tracking
- **Upload Meals**: Drag-and-drop image upload
- **AI Analysis**: Automatic nutrition analysis from photos
- **Manual Entry**: Text-only meal logging
- **Meal Details**: View comprehensive nutrition information
- **Edit Meals**: Correct AI predictions and track accuracy
- **Delete Meals**: Remove meals from history

### Dashboard
- Daily nutrition summary (calories, macros)
- Weekly/monthly trends
- Meal type breakdown (breakfast, lunch, dinner, snacks)
- Recent meals list
- Quick stats (total meals, average calories)

### Pagination
- Infinite scroll for meal lists
- Server-side pagination (20 items per page, max 100)
- Efficient loading with React Query
- Optimized for performance with large datasets

### Analytics (Admin)
- AI accuracy metrics by nutrition field
- Systematic bias detection
- Location-based accuracy analysis
- Correction trends over time
- Confidence calibration charts

### Responsive Design
- Mobile-first approach
- Tablet and desktop optimized
- Touch-friendly interface
- Adaptive layouts

## Project Structure

```
src/
├── components/           # Reusable UI components
│   ├── Layout.tsx       # Main layout with nav
│   ├── MealCard.tsx     # Meal display card
│   ├── NutritionChart.tsx # Charts for dashboard
│   └── ...
├── pages/               # Route pages
│   ├── Login.tsx
│   ├── Register.tsx
│   ├── Dashboard.tsx
│   ├── MealUpload.tsx
│   ├── MealList.tsx
│   ├── MealDetail.tsx
│   └── Analytics.tsx
├── lib/                 # Utilities and config
│   ├── api.ts          # API client
│   ├── auth.tsx        # Auth context
│   └── utils.ts        # Helper functions
├── types/               # TypeScript types
│   └── index.ts
├── App.tsx             # Main app component
├── main.tsx            # Entry point
└── index.css           # Global styles

## Getting Started

### Prerequisites

- Node.js 18+ and npm/yarn
- Backend API running on http://localhost:8080

### Installation

```bash
# Install dependencies
npm install

# Copy environment variables
cp .env.example .env

# Start development server
npm run dev
```

The app will be available at http://localhost:3000

### Environment Variables

```env
VITE_API_BASE_URL=http://localhost:8080
VITE_APP_NAME=Nutritheous
```

## Available Scripts

- `npm run dev` - Start development server with hot reload
- `npm run build` - Build for production
- `npm run preview` - Preview production build locally
- `npm run lint` - Run ESLint
- `npm run type-check` - Run TypeScript type checking

## API Integration

The app integrates with the Nutritheous Spring Boot backend:

### Authentication
- `POST /api/auth/register` - User registration
- `POST /api/auth/login` - User login

### Meals
- `POST /api/meals/upload` - Upload meal with image
- `GET /api/meals/paginated` - Get meals (paginated)
- `GET /api/meals/paginated/range` - Get meals by date range
- `GET /api/meals/paginated/type/{type}` - Get meals by type
- `GET /api/meals/{id}` - Get meal details
- `PUT /api/meals/{id}` - Update meal
- `DELETE /api/meals/{id}` - Delete meal
- `GET /api/meals/count` - Get total meal count

### Analytics
- `GET /api/analytics/corrections/overall-accuracy` - AI accuracy stats
- `GET /api/analytics/corrections/systematic-bias` - Bias detection
- `GET /api/analytics/corrections/accuracy-by-location` - Location-based accuracy
- `GET /api/analytics/corrections/trends` - Correction trends

## Key Features Implementation

### JWT Authentication
- Token stored in localStorage
- Automatic injection in API requests via Axios interceptor
- Auto-logout on 401 Unauthorized
- Protected routes with ProtectedRoute component

### Pagination
- Server-side pagination using Spring Data Page
- Client-side state management with React Query
- Infinite scroll support
- Configurable page sizes (default 20, max 100)

### File Upload
- Drag-and-drop interface with React Dropzone
- Image preview before upload
- File validation (type, size)
- Progress indication
- Error handling

### Real-time Notifications
- Success/error toasts with React Hot Toast
- Configurable duration
- Custom styling
- Icon support

### Responsive Charts
- Recharts for data visualization
- Responsive container
- Interactive tooltips
- Multiple chart types (bar, line, pie)

## Performance Optimizations

- Code splitting with React.lazy()
- Image lazy loading
- React Query caching (5-minute stale time)
- Memoized expensive calculations
- Debounced search inputs
- Optimized re-renders with useMemo/useCallback

## Browser Support

- Chrome/Edge 90+
- Firefox 88+
- Safari 14+
- Mobile browsers (iOS Safari, Chrome Mobile)

## Production Build

```bash
# Build for production
npm run build

# Output in dist/ directory
# Serve with any static file server
```

### Deployment Options

1. **Vercel/Netlify**: Zero-config deployment
2. **Docker**: Nginx + static files
3. **S3 + CloudFront**: AWS static hosting
4. **GitHub Pages**: Free hosting for public repos

## Docker Deployment

```dockerfile
# Build stage
FROM node:18-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Production stage
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

## Testing

```bash
# Run unit tests (when implemented)
npm run test

# Run E2E tests (when implemented)
npm run test:e2e
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run linting and type checking
5. Submit a pull request

## License

Proprietary - Nutritheous

## Support

For issues or questions, contact the development team.

---

**Note**: This is a reference implementation for the Nutritheous platform. Customize as needed for your specific requirements.
