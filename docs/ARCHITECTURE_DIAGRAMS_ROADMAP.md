# Architecture Diagrams Roadmap

**Purpose:** Visual documentation of NutriLens architecture at multiple abstraction levels for quick understanding and onboarding.

**Location:** `/docs/architecture/`

---

## Diagram Hierarchy

### Level 1: System Overview (High-Level)
**Audience:** Product managers, stakeholders, new developers
**Focus:** What the system does and external dependencies

1. **`01-system-context.mmd`** - Complete system context
   - User interactions
   - External services (OpenAI, Google Cloud, PostgreSQL)
   - Data flows at system boundary

2. **`02-user-journey.mmd`** - End-to-end user flows
   - Photo upload journey
   - Text-only meal entry
   - Meal history retrieval
   - Analytics/statistics

3. **`03-deployment-architecture.mmd`** - Deployment view
   - Backend services
   - Database
   - Cloud storage
   - External APIs
   - Network boundaries

---

### Level 2: Subsystem Interactions (Mid-Level)
**Audience:** Developers, architects
**Focus:** How subsystems interact and collaborate

4. **`04-subsystem-overview.mmd`** - All subsystems and their relationships
   - Auth
   - Meal Management
   - AI Analysis
   - Storage
   - Correction Tracking
   - Statistics
   - Monitoring

5. **`05-meal-upload-flow.mmd`** - Detailed meal upload sequence
   - Controller → Service → Analyzer
   - Photo metadata extraction
   - Location intelligence
   - AI vision analysis
   - Storage orchestration
   - Database persistence

6. **`06-ai-analysis-pipeline.mmd`** - AI analysis subsystem
   - Image processing
   - Context building (location, time)
   - OpenAI Vision API integration
   - Response validation
   - Correction tracking

7. **`07-data-flow.mmd`** - Complete data flow
   - Request → Response path
   - Data transformations (DTO → Entity)
   - Validation points
   - Error handling paths

8. **`08-security-flow.mmd`** - Security architecture
   - JWT authentication
   - Authorization filters
   - Rate limiting
   - CORS configuration
   - Input validation

---

### Level 3: Component Details (Low-Level)
**Audience:** Developers working on specific features
**Focus:** Internal component structure and patterns

9. **`09-meal-domain-model.mmd`** - Meal aggregate
   - Meal entity
   - Value objects
   - Repository pattern
   - Service layer

10. **`10-analyzer-components.mmd`** - Analyzer subsystem internals
    - AnalyzerService orchestration
    - OpenAIVisionService
    - ImageProcessingService
    - PhotoMetadataService
    - LocationContextService
    - Strategy pattern

11. **`11-storage-components.mmd`** - Storage abstraction
    - GoogleCloudStorageService
    - Interface contracts
    - Error handling
    - Health checks

12. **`12-correction-tracking.mmd`** - AI correction system
    - AiCorrectionLog entity
    - Telemetry collection
    - Validation service
    - Analytics aggregation

13. **`13-statistics-aggregation.mmd`** - Statistics subsystem
    - Daily/weekly/monthly aggregations
    - Calculation strategies
    - Caching layer
    - Performance optimization

---

### Level 4: Design Patterns Catalog
**Audience:** Developers learning the codebase
**Focus:** Reusable patterns and best practices

14. **`14-design-patterns-overview.mmd`** - All patterns in use
    - Repository Pattern
    - Service Layer Pattern
    - DTO Pattern
    - Strategy Pattern
    - Factory Pattern
    - Builder Pattern
    - Dependency Injection
    - Decorator Pattern (rate limiting)

15. **`15-error-handling-patterns.mmd`** - Error handling architecture
    - Exception hierarchy
    - GlobalExceptionHandler
    - Circuit breaker pattern
    - Retry strategies
    - Fallback mechanisms

16. **`16-testing-patterns.mmd`** - Test architecture
    - Unit test patterns
    - Property-based testing
    - Integration test patterns
    - Test fixtures and builders
    - Mutation testing approach

---

## Cross-Cutting Concerns

17. **`17-observability.mmd`** - Monitoring and logging
    - Health checks
    - Metrics collection
    - Logging strategy
    - Tracing (if implemented)

18. **`18-configuration-management.mmd`** - Configuration architecture
    - Environment-based config
    - Secrets management
    - Feature flags (if any)
    - Profile activation

19. **`19-api-contracts.mmd`** - API design
    - REST endpoints
    - Request/Response DTOs
    - OpenAPI specification
    - Versioning strategy

20. **`20-database-schema.mmd`** - Database architecture
    - Entity relationships
    - Migration strategy (Flyway)
    - Indexes and performance
    - Data consistency

---

## Implementation Plan

### Phase 1: Core System Understanding (Priority 1-8)
These diagrams are essential for understanding how the system works end-to-end.

**Order:**
1. System Context (01) - What does the system do?
2. User Journey (02) - How do users interact?
3. Subsystem Overview (04) - What are the main components?
4. Meal Upload Flow (05) - Most important user flow
5. AI Analysis Pipeline (06) - Core differentiator
6. Data Flow (07) - Complete request/response
7. Security Flow (08) - Critical for compliance
8. Deployment Architecture (03) - How is it deployed?

### Phase 2: Component Deep-Dive (Priority 9-13)
These help developers working on specific features.

**Order:**
1. Meal Domain Model (09) - Core business logic
2. Analyzer Components (10) - Most complex subsystem
3. Correction Tracking (12) - Telemetry system
4. Statistics Aggregation (13) - Analytics
5. Storage Components (11) - Infrastructure

### Phase 3: Patterns & Best Practices (Priority 14-20)
These help maintain consistency and quality.

**Order:**
1. Design Patterns Overview (14) - Reusable patterns
2. Error Handling Patterns (15) - Resilience
3. Testing Patterns (16) - Quality assurance
4. API Contracts (19) - External interface
5. Database Schema (20) - Data model
6. Observability (17) - Operations
7. Configuration Management (18) - DevOps

---

## Diagram Standards

### Mermaid Syntax
- Use descriptive node names
- Keep diagrams focused (max 15-20 nodes)
- Include legends where helpful
- Use consistent color coding:
  - Blue: User-facing components
  - Green: Internal services
  - Orange: External dependencies
  - Red: Security/validation layers
  - Purple: Data stores

### File Naming
- Format: `NN-descriptive-name.mmd`
- Use hyphens for spaces
- Lowercase only
- Sequential numbering

### Documentation
Each diagram file should include:
```markdown
# Diagram Title

## Purpose
What this diagram shows and who should use it

## Key Components
Brief description of main elements

## Interactions
How components interact

## Notes
Important details or considerations
```

---

## Usage

### Viewing Diagrams
1. Use Mermaid Live Editor: https://mermaid.live/
2. Use VS Code with Mermaid preview extension
3. GitHub renders Mermaid natively in markdown

### Updating Diagrams
1. Diagrams should be updated when architecture changes
2. All diagrams reviewed during architecture decision records (ADRs)
3. Mark outdated diagrams with `[OUTDATED]` prefix

### Integration with Documentation
- Link from main README.md
- Reference in feature documentation
- Include in onboarding materials

---

## Benefits

**For New Developers:**
- Understand system in hours instead of days
- Visual learning path from high-level to details
- Clear separation of concerns

**For Architects:**
- Document design decisions visually
- Communicate changes effectively
- Review architectural consistency

**For Product/Stakeholders:**
- Understand system capabilities
- See complexity and dependencies
- Plan features with technical context

---

*Status: Roadmap created - Implementation in progress*
