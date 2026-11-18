export type Role = "SUPER_ADMIN" | "ADMIN" | "OPERATIONS_MANAGER" | "FLIGHT_AGENT" | "CHECKIN_AGENT" | "PASSENGER";
export const STAFF_ROLES: Role[] = ["SUPER_ADMIN", "ADMIN", "OPERATIONS_MANAGER", "FLIGHT_AGENT", "CHECKIN_AGENT"];
export const ROLE_LABELS: Record<Role, string> = {
  SUPER_ADMIN: "Super admin", ADMIN: "Administrator", OPERATIONS_MANAGER: "Operations manager",
  FLIGHT_AGENT: "Flight agent", CHECKIN_AGENT: "Check-in agent", PASSENGER: "Passenger",
};

export interface User {
  id: string; email: string; firstName: string; lastName: string; phone?: string; roles: Role[];
  enabled: boolean; lastLoginAt?: string; createdAt: string; lockedUntil?: string;
}
export interface TokenResponse { accessToken: string; refreshToken: string; expiresIn: number; user: User }
export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }

export interface Airport { code: string; name: string; city: string; country: string; timezone: string }
export interface SeatLayout { businessRows: number; businessLayout: string; economyRows: number; economyLayout: string }
export interface Aircraft {
  id: string; registration: string; model: string; manufacturer: string; capacity: number;
  businessSeats: number; economySeats: number; status: string; layout: SeatLayout;
}
export type FlightStatus = "SCHEDULED" | "DELAYED" | "BOARDING" | "DEPARTED" | "ARRIVED" | "CANCELLED";
export const FLIGHT_STATUSES: FlightStatus[] = ["SCHEDULED", "DELAYED", "BOARDING", "DEPARTED", "ARRIVED", "CANCELLED"];
export interface Flight {
  id: string; flightNumber: string; origin: Airport; destination: Airport; aircraft: Aircraft;
  departureTime: string; arrivalTime: string; estimatedDepartureTime: string; durationMinutes: number; capacity: number;
  status: FlightStatus; delayMinutes: number; statusReason?: string; gate?: string; terminal?: string;
  economyFare: number; businessFare: number; currency: string; version: number; updatedAt: string;
}
export interface FlightStats {
  total: number; scheduled: number; delayed: number; cancelled: number; boarding: number; departed: number;
  arrived: number; activeNow: number; departingToday: number;
}

export type Cabin = "ECONOMY" | "BUSINESS";
export interface SeatMap {
  flightId: string; flightNumber: string; aircraftModel: string; businessRows: number; businessLayout: string;
  economyRows: number; economyLayout: string; occupied: string[]; economyFare: number; businessFare: number;
  currency: string; available: number;
}
export interface BookingPassenger {
  id: string; firstName: string; lastName: string; dateOfBirth?: string; nationality?: string; passportMasked?: string;
  email?: string; cabin: Cabin; seatNumber: string; fare: number; checkedIn: boolean; checkedInAt?: string;
}
export interface Booking {
  id: string; pnr: string; flightId: string; flightNumber: string; origin: string; destination: string;
  departureTime: string; arrivalTime: string; status: "CONFIRMED" | "CANCELLED"; totalAmount: number; currency: string;
  contactEmail: string; contactPhone?: string; paymentReference?: string; cardBrand?: string; cardLast4?: string;
  channel: string; cancellationReason?: string; cancelledAt?: string; createdAt: string; passengers: BookingPassenger[];
  payments: { type: string; amount: number; status: string; reference: string; at: string }[];
}

export interface Passenger {
  id: string; userId?: string; firstName: string; lastName: string; email: string; phone?: string; dateOfBirth?: string;
  nationality?: string; passportNumber?: string; passportExpiry?: string; addressLine?: string; city?: string;
  country?: string; loyaltyTier: string; loyaltyPoints: number; notes?: string; createdAt: string; updatedAt: string;
}
export interface TravelRecord {
  bookingId: string; pnr: string; flightNumber: string; origin: string; destination: string; departureTime: string;
  status: string; amount?: number; currency?: string; checkedIn: boolean;
}
export interface PassengerDetail {
  profile: Passenger; travelHistory: TravelRecord[]; totalTrips: number; upcomingTrips: number; lifetimeSpend: number;
}

export interface BoardingPass {
  id: string; passengerId: string; passengerName: string; pnr: string; flightNumber: string; origin: string;
  destination: string; departureTime: string; seatNumber: string; cabin: Cabin; boardingPassNumber: string;
  sequenceNumber: number; boardingGroup: string; gate?: string; boardingTime: string; checkedInAt: string; channel: string;
}
export interface CheckinView {
  booking: Booking;
  eligibility: { open: boolean; opensAt: string; closesAt: string; reason?: string };
  boardingPasses: BoardingPass[];
}
export interface NotificationItem {
  id: string; eventType: string; channel: "EMAIL" | "SMS"; recipient: string; subject?: string; body: string;
  pnr?: string; status: string; providerRef?: string; createdAt: string;
}
export interface Dashboard {
  kpis: Record<string, number>;
  flightStatus: { status: FlightStatus; count: number }[];
  bookingTrend: { date: string; bookings: number; revenue: number; passengers: number }[];
  topRoutes: { route: string; bookings: number; passengers: number; revenue: number }[];
  recentActivity: { kind: string; message: string; ref?: string; occurred_at: string }[];
  alerts: { severity: "critical" | "warning" | "info"; title: string; detail: string }[];
  syntheticDataIncluded: boolean;
  generatedAt: string;
}
export interface AuditEntry {
  id: string; actor_email: string; actor_roles?: string; action: string; entity_type: string; entity_id?: string;
  details?: string; ip_address?: string; source: string; occurred_at: string;
}
