import { VendorBookingDetail } from "@/components/vendor-os/vendor-bookings";

export default async function VendorBookingDetailPage({ params }: { params: Promise<{ bookingId: string }> }) {
  return <VendorBookingDetail bookingId={(await params).bookingId} />;
}
