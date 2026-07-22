import { Redirect, Tabs } from "expo-router";
import { Text } from "react-native";
import { tokens } from "@wedjan/ui-tokens";
import { useAuth } from "@/lib/auth-context";

const HOME_TITLE: Record<string, string> = {
  CUSTOMER: "My Events",
  VENDOR: "Dashboard",
  FREELANCER: "Shifts",
  ADMIN: "Admin",
};

export default function AppLayout() {
  const { status, activeRole } = useAuth();

  if (status === "anonymous") {
    return <Redirect href="/(auth)/login" />;
  }
  if (status === "loading" || !activeRole) {
    return null;
  }

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: tokens.colors.brandPink,
        tabBarInactiveTintColor: tokens.colors.textMuted,
        tabBarStyle: { backgroundColor: tokens.colors.surface },
      }}
    >
      <Tabs.Screen
        name="home"
        options={{
          title: HOME_TITLE[activeRole] ?? "Home",
          tabBarIcon: ({ color }) => <Text style={{ color, fontSize: 18 }}>◈</Text>,
        }}
      />
      <Tabs.Screen
        name="explore"
        options={{
          title: "Explore",
          tabBarIcon: ({ color }) => <Text style={{ color, fontSize: 18 }}>⌕</Text>,
        }}
      />
      <Tabs.Screen
        name="inspiration"
        options={{
          title: "Inspiration",
          tabBarIcon: ({ color }) => <Text style={{ color, fontSize: 18 }}>▦</Text>,
        }}
      />
      <Tabs.Screen
        name="bookings"
        options={{
          title: activeRole === "VENDOR" ? "Requests" : "Bookings",
          href: activeRole === "CUSTOMER" || activeRole === "VENDOR" ? undefined : null,
          tabBarIcon: ({ color }) => <Text style={{ color, fontSize: 18 }}>▦</Text>,
        }}
      />
      <Tabs.Screen
        name="settings"
        options={{
          title: "Settings",
          tabBarIcon: ({ color }) => <Text style={{ color, fontSize: 18 }}>⚙</Text>,
        }}
      />
      <Tabs.Screen
        name="vendor"
        options={{
          title: "My Listing",
          href: activeRole === "VENDOR" ? undefined : null,
          tabBarIcon: ({ color }) => <Text style={{ color, fontSize: 18 }}>◇</Text>,
        }}
      />
      <Tabs.Screen
        name="availability"
        options={{
          href: null,
          title: "Availability",
        }}
      />
    </Tabs>
  );
}
