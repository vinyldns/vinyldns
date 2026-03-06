# VinylDNS React Frontend

This module is a React/TypeScript rewrite of the original AngularJS portal at `modules/portal`.
It connects to the same Play Framework backend API with **no changes required server-side**.

## Tech Stack

| Tool | Purpose |
|------|---------|
| [React 19](https://react.dev) | UI library |
| [TypeScript](https://www.typescriptlang.org) | Type safety |
| [Vite](https://vitejs.dev) | Build tool & dev server |
| [React Router v6](https://reactrouter.com) | Client-side routing |
| [TanStack Query](https://tanstack.com/query) | Data fetching, caching, mutations |
| [React Hook Form](https://react-hook-form.com) | Form handling |
| [Axios](https://axios-http.com) | HTTP client |
| [Bootstrap 5](https://getbootstrap.com) | CSS framework |
| [Bootstrap Icons](https://icons.getbootstrap.com) | Icon set |
| [Vitest](https://vitest.dev) + Testing Library | Unit & integration tests |

## Folder Structure

```
src/
├── App.tsx                    # Root component with all routes
├── index.tsx                  # React entry point
├── styles/
│   └── vinyldns.css           # Global VinylDNS styles
├── types/                     # TypeScript interfaces (zone, group, record, etc.)
├── services/                  # API layer (Axios) – mirrors Angular services
│   ├── api.ts                 # Axios instance + CSRF + urlBuilder
│   ├── zonesService.ts
│   ├── groupsService.ts
│   ├── recordsService.ts
│   ├── profileService.ts
│   └── dnsChangeService.ts
├── hooks/                     # Custom React hooks – replace Angular controllers
│   ├── usePaging.ts           # Pagination state (mirrors service.paging.js)
│   ├── useZones.ts
│   ├── useGroups.ts
│   ├── useRecords.ts
│   └── useDnsChanges.ts
├── contexts/                  # React context providers
│   ├── AlertContext.tsx       # Global alert/notification system
│   └── ProfileContext.tsx     # Current user profile
├── components/                # Reusable UI components
│   ├── common/
│   │   ├── Layout.tsx         # Sidebar + navigation (mirrors main.scala.html)
│   │   ├── AlertBanner.tsx    # Toast alerts
│   │   ├── Pagination.tsx     # Next / Previous paging
│   │   └── LoadingSpinner.tsx
│   ├── zones/
│   │   ├── ZonesTable.tsx
│   │   └── ZoneForm.tsx
│   ├── groups/
│   │   ├── GroupsTable.tsx
│   │   ├── GroupForm.tsx
│   │   └── GroupMemberList.tsx
│   ├── records/
│   │   └── RecordsTable.tsx
│   └── dnsChanges/
│       ├── DnsChangesTable.tsx
│       └── DnsChangeForm.tsx
├── pages/                     # One file per route
│   ├── LoginPage.tsx
│   ├── ZonesPage.tsx
│   ├── ZoneDetailPage.tsx
│   ├── GroupsPage.tsx
│   ├── GroupDetailPage.tsx
│   ├── RecordsPage.tsx
│   ├── DnsChangesPage.tsx
│   ├── DnsChangeDetailPage.tsx
│   └── DnsChangeNewPage.tsx
├── utils/
│   └── dateUtils.ts           # Date formatting + error helpers
└── test/
    ├── setup.ts               # Vitest / Testing Library setup
    ├── utils/dateUtils.test.ts
    └── hooks/usePaging.test.ts
```

## Routes

| Route | Page |
|-------|------|
| `/login` | Log-in page |
| `/zones` | Zones list |
| `/zones/:id` | Zone detail + records |
| `/groups` | Groups list |
| `/groups/:id` | Group detail + members |
| `/recordsets` | Global RecordSet search |
| `/dnschanges` | DNS Batch Changes list |
| `/dnschanges/new` | Create a new Batch Change |
| `/dnschanges/:id` | Batch Change detail |

## Development

### Prerequisites
- Node.js 18+ 
- The VinylDNS Play backend running on `http://localhost:9001`

### Install dependencies
```bash
cd modules/frontend
npm install
```

### Start the dev server
```bash
npm run dev
```
Open [http://localhost:9001](http://localhost:9001).

API requests are proxied to `http://localhost:9001` via `vite.config.ts`.

### Build for production
```bash
npm run build
# Output is in dist/
```

### Run tests
```bash
npm test
# or with coverage
npm run test:coverage
```

## Angular → React mapping

| Angular concept | React equivalent |
|----------------|------------------|
| `angular.module` | App.tsx + React Router |
| `$scope` | `useState` / `useReducer` |
| `$http` | `axios` (via service layer) |
| Angular service | `src/services/*.ts` |
| Angular controller | `src/hooks/*.ts` + page component |
| Angular directive | React component |
| `$q` promises | `async/await` + TanStack Query |
| `ngRepeat` | `Array.map()` |
| `ng-model` | `react-hook-form` |
| Bootstrap 3 modals | Bootstrap 5 (data-bs-* attrs) |
| `ui-sref` / `$location` | `<Link>` / `useNavigate` |
