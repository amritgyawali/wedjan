import { CustomerBookingDetail } from "@/components/marketplace/booking/customer-bookings";

export default async function BookingDetailPage({ params }: { params: Promise<{ bookingId: string }> }) {
  return <CustomerBookingDetail bookingId={(await params).bookingId} />;
}
