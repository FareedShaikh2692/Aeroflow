import { z } from "zod";

export const PASSWORD_RULE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{10,72}$/;
const name = z.string().trim().min(1, "Required").max(80).regex(/^[\p{L} .'-]+$/u, "Letters, spaces, . ' - only");
const phone = z.string().trim().regex(/^\+?[0-9 ()-]{6,30}$/, "Enter a valid phone number").optional().or(z.literal(""));

export const loginSchema = z.object({
  email: z.string().trim().email("Enter a valid e-mail address"),
  password: z.string().min(1, "Password is required"),
});

export const registerSchema = z.object({
  firstName: name, lastName: name,
  email: z.string().trim().email("Enter a valid e-mail address"),
  phone,
  password: z.string().regex(PASSWORD_RULE, "10+ characters with upper, lower, number and symbol"),
  confirm: z.string(),
}).refine((d) => d.password === d.confirm, { path: ["confirm"], message: "Passwords do not match" });

export const searchSchema = z.object({
  origin: z.string().length(3, "Choose an origin"),
  destination: z.string().length(3, "Choose a destination"),
  date: z.string().min(10, "Choose a date"),
  passengers: z.coerce.number().int().min(1).max(9),
  cabin: z.enum(["ECONOMY", "BUSINESS"]),
}).refine((d) => d.origin !== d.destination, { path: ["destination"], message: "Destination must differ from origin" });

export const travellerSchema = z.object({
  firstName: name,
  lastName: name,
  dateOfBirth: z.string().optional().or(z.literal(""))
    .refine((v) => !v || new Date(v) < new Date(), "Date of birth must be in the past"),
  nationality: z.string().trim().toUpperCase().regex(/^[A-Z]{2}$/, "2-letter code, e.g. AE").optional().or(z.literal("")),
  passportNumber: z.string().trim().toUpperCase().regex(/^[A-Z0-9]{5,20}$/, "5–20 letters or digits").optional().or(z.literal("")),
  email: z.string().trim().email("Invalid e-mail").optional().or(z.literal("")),
});

export const passengersFormSchema = z.object({
  travellers: z.array(travellerSchema).min(1).max(9),
  contactEmail: z.string().trim().email("Enter a valid e-mail address"),
  contactPhone: phone,
});

export function luhn(num: string) {
  const d = num.replace(/\D/g, "");
  if (d.length < 12 || d.length > 19) return false;
  let sum = 0;
  let dbl = false;
  for (let i = d.length - 1; i >= 0; i--) {
    let n = Number(d[i]);
    if (dbl) { n *= 2; if (n > 9) n -= 9; }
    sum += n;
    dbl = !dbl;
  }
  return sum % 10 === 0;
}

export const paymentSchema = z.object({
  cardHolder: z.string().trim().min(2, "Name on card is required").max(100),
  cardNumber: z.string().refine(luhn, "Card number is invalid"),
  expiry: z.string().regex(/^(0[1-9]|1[0-2])\/\d{2}$/, "Use MM/YY").refine((v) => {
    const [m, y] = v.split("/").map(Number);
    const end = new Date(2000 + y!, m!, 1);
    return end > new Date();
  }, "Card has expired"),
  cvv: z.string().regex(/^\d{3,4}$/, "3 or 4 digits"),
  acceptTerms: z.literal(true, { errorMap: () => ({ message: "Please accept the fare rules" }) }),
});

export const pnrSchema = z.object({
  pnr: z.string().trim().toUpperCase().regex(/^[A-Z0-9]{6}$/, "Booking reference is 6 letters/numbers"),
  lastName: z.string().trim().min(1, "Last name is required").max(80),
});

export const flightSchema = z.object({
  flightNumber: z.string().trim().toUpperCase().regex(/^[A-Z0-9]{2}\d{1,4}$/, "e.g. AF201"),
  originCode: z.string().length(3, "Required"),
  destinationCode: z.string().length(3, "Required"),
  aircraftId: z.string().uuid("Choose an aircraft"),
  departureTime: z.string().min(1, "Required"),
  arrivalTime: z.string().min(1, "Required"),
  economyFare: z.coerce.number().min(1, "Must be positive"),
  businessFare: z.coerce.number().min(1, "Must be positive"),
  gate: z.string().max(6).optional(),
  terminal: z.string().max(4).optional(),
}).refine((d) => d.originCode !== d.destinationCode, { path: ["destinationCode"], message: "Must differ from origin" })
  .refine((d) => !d.departureTime || !d.arrivalTime || new Date(d.arrivalTime) > new Date(d.departureTime),
    { path: ["arrivalTime"], message: "Arrival must be after departure" })
  .refine((d) => d.businessFare >= d.economyFare, { path: ["businessFare"], message: "Must be ≥ economy fare" });

export const passengerProfileSchema = z.object({
  firstName: name, lastName: name,
  email: z.string().trim().email("Enter a valid e-mail address"),
  phone,
  dateOfBirth: z.string().optional().or(z.literal("")),
  nationality: z.string().trim().toUpperCase().regex(/^[A-Z]{2}$/, "2-letter code").optional().or(z.literal("")),
  passportNumber: z.string().trim().toUpperCase().regex(/^[A-Z0-9]{5,20}$/, "5–20 letters or digits").optional().or(z.literal("")),
  passportExpiry: z.string().optional().or(z.literal("")),
  city: z.string().max(80).optional(),
  country: z.string().max(80).optional(),
  notes: z.string().max(1000).optional(),
});

export const userSchema = z.object({
  firstName: name, lastName: name,
  email: z.string().trim().email("Enter a valid e-mail address"),
  phone,
  password: z.string().regex(PASSWORD_RULE, "10+ chars with upper, lower, number, symbol").optional().or(z.literal("")),
  roles: z.array(z.string()).min(1, "Pick at least one role"),
  enabled: z.boolean(),
});

/** Converts "" to undefined so optional API fields are omitted rather than sent empty. */
export function clean<T extends Record<string, unknown>>(o: T): T {
  return Object.fromEntries(Object.entries(o).map(([k, v]) => [k, v === "" ? undefined : v])) as T;
}
