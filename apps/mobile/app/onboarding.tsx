import { router } from "expo-router";
import { useRef, useState } from "react";
import {
  Dimensions,
  FlatList,
  Pressable,
  StyleSheet,
  Text,
  View,
  type ViewToken,
} from "react-native";
import { tokens } from "@wedjan/ui-tokens";

const { width } = Dimensions.get("window");

const SLIDES = [
  {
    key: "plan",
    title: "Plan every celebration",
    body: "From mehndi to reception — guest lists, budgets and checklists in one place.",
  },
  {
    key: "book",
    title: "Book at real prices",
    body: "Every vendor is verified and every package has a real price. Payments are escrow-protected.",
  },
  {
    key: "work",
    title: "Work great events",
    body: "Vendors staff their crews here — pick up shifts and get paid within 72 hours.",
  },
];

export default function Onboarding() {
  const [index, setIndex] = useState(0);
  const listRef = useRef<FlatList>(null);

  const onViewableItemsChanged = useRef(
    ({ viewableItems }: { viewableItems: ViewToken[] }) => {
      const first = viewableItems[0];
      if (first?.index != null) setIndex(first.index);
    },
  ).current;

  return (
    <View style={styles.container}>
      <Pressable style={styles.skip} onPress={() => router.replace("/(auth)/login")}>
        <Text style={styles.skipText}>Skip</Text>
      </Pressable>
      <FlatList
        ref={listRef}
        data={SLIDES}
        keyExtractor={(item) => item.key}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        onViewableItemsChanged={onViewableItemsChanged}
        renderItem={({ item }) => (
          <View style={[styles.slide, { width }]}>
            <Text style={styles.wordmark}>wedjan</Text>
            <Text style={styles.title}>{item.title}</Text>
            <Text style={styles.body}>{item.body}</Text>
          </View>
        )}
      />
      <View style={styles.dots}>
        {SLIDES.map((slide, i) => (
          <View key={slide.key} style={[styles.dot, i === index && styles.dotActive]} />
        ))}
      </View>
      <View style={styles.actions}>
        <Pressable
          style={styles.primaryButton}
          onPress={() => router.replace("/(auth)/signup")}
        >
          <Text style={styles.primaryButtonText}>Get started</Text>
        </Pressable>
        <Pressable onPress={() => router.replace("/(auth)/login")}>
          <Text style={styles.secondaryText}>I already have an account</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: tokens.colors.background,
    paddingTop: 64,
  },
  skip: {
    position: "absolute",
    top: 56,
    right: 24,
    zIndex: 2,
    minHeight: tokens.layout.minTouchTarget,
    justifyContent: "center",
  },
  skipText: {
    color: tokens.colors.textMuted,
    fontSize: 14,
    fontWeight: "600",
  },
  slide: {
    flex: 1,
    justifyContent: "center",
    paddingHorizontal: 32,
  },
  wordmark: {
    color: tokens.colors.brandPink,
    fontSize: 32,
    fontWeight: "700",
    marginBottom: 24,
  },
  title: {
    color: tokens.colors.onSurface,
    fontSize: 34,
    fontWeight: "700",
    lineHeight: 40,
    marginBottom: 16,
  },
  body: {
    color: tokens.colors.secondary,
    fontSize: 16,
    lineHeight: 24,
  },
  dots: {
    flexDirection: "row",
    justifyContent: "center",
    gap: 8,
    marginBottom: 24,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: tokens.colors.surfaceVariant,
  },
  dotActive: {
    backgroundColor: tokens.colors.brandPink,
  },
  actions: {
    gap: 16,
    alignItems: "center",
    paddingHorizontal: 32,
    paddingBottom: 48,
  },
  primaryButton: {
    width: "100%",
    minHeight: 52,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: tokens.radii.pill,
    backgroundColor: tokens.colors.brandPink,
  },
  primaryButtonText: {
    color: "#fff",
    fontSize: 16,
    fontWeight: "700",
  },
  secondaryText: {
    color: tokens.colors.secondary,
    fontSize: 14,
    fontWeight: "600",
    padding: 8,
  },
});
