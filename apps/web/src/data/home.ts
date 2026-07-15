export type SelectOption = {
  value: string;
  label: string;
};

export type Venue = {
  title: string;
  locations: string;
  image: string;
  alt: string;
};

export type Service = {
  title: string;
  description: string;
  image: string;
  alt: string;
};

export type Category = {
  title: string;
  image: string;
  alt: string;
};

export type WeddingStory = {
  couple: string;
  description: string;
  date: string;
  image?: string;
  alt?: string;
  video?: boolean;
};

export type GalleryItem = {
  title: string;
  image: string;
  alt: string;
  variant: "feature" | "standard" | "wide" | "square";
  eyebrow?: string;
  description?: string;
};

export type Vendor = {
  name: string;
  category: string;
  price: string;
  priceSuffix: string;
  rating?: string;
  image?: string;
  alt?: string;
};

export const navLinks = [
  { label: "Venues", href: "/search?category=venues" },
  { label: "Vendors", href: "/search" },
  { label: "Photos", href: "/inspiration" },
  { label: "Real Weddings", href: "/inspiration?eventType=WEDDING" },
  { label: "Blog", href: "/inspiration" },
  { label: "Shop", href: "/search" },
] as const;

export const vendorTypes: SelectOption[] = [
  { value: "venues", label: "Venues" },
  { value: "photographers", label: "Photographers" },
  { value: "makeup", label: "Makeup Artists" },
  { value: "decorators", label: "Wedding Decorators" },
  { value: "catering", label: "Caterers" },
];

export const cities: SelectOption[] = [
  { value: "delhi-ncr", label: "Delhi NCR" },
  { value: "mumbai", label: "Mumbai" },
  { value: "bangalore", label: "Bangalore" },
  { value: "pune", label: "Pune" },
  { value: "jaipur", label: "Jaipur" },
];

export const venues: Venue[] = [
  {
    title: "4 Star & Above Hotels",
    locations: "Mumbai | Bangalore | Pune | More",
    image: "/images/wedjan/venue-hotel.png",
    alt: "A warmly illuminated luxury hotel prepared to host a wedding",
  },
  {
    title: "Banquet Halls",
    locations: "Mumbai | Bangalore | Pune | More",
    image: "/images/wedjan/venue-banquet.png",
    alt: "An elegant banquet hall with round tables and floral settings",
  },
  {
    title: "Marriage Garden / Lawns",
    locations: "Mumbai | Bangalore | Pune | More",
    image: "/images/wedjan/venue-lawn.png",
    alt: "A colorful outdoor wedding lawn surrounded by mountain scenery",
  },
];

export const services: Service[] = [
  {
    title: "Beauty at Home",
    description: "wedjan at-home makeup services for the whole family",
    image: "/images/wedjan/service-beauty.png",
    alt: "A joyful bridal party getting ready together",
  },
  {
    title: "Planning Genie",
    description: "Plan your dream wedding around the budget that suits you",
    image: "/images/wedjan/service-planning.png",
    alt: "A newly married couple smiling during their ceremony",
  },
];

export const categories: Category[] = [
  {
    title: "Bridal Wear",
    image: "/images/wedjan/category-bridal-wear.png",
    alt: "A bride in an intricately embroidered red lehenga",
  },
  {
    title: "Bridal Makeup",
    image: "/images/wedjan/category-makeup.png",
    alt: "A bride wearing luminous makeup and traditional jewelry",
  },
  {
    title: "Photographers",
    image: "/images/wedjan/category-photographers.png",
    alt: "A couple posing beneath a floral arch",
  },
  {
    title: "Invitations",
    image: "/images/wedjan/category-invitations.png",
    alt: "A coordinated set of elegant wedding invitation cards",
  },
  {
    title: "Catering",
    image: "/images/wedjan/category-catering.png",
    alt: "A stylish outdoor wedding refreshment cart",
  },
];

export const weddingStories: WeddingStory[] = [
  {
    couple: "Gurmehar and Anirudh",
    description:
      "Charming Udaipur wedding with a timeless red bride and the dreamiest white floral mandap.",
    date: "25 January 2026",
    image: "/images/wedjan/story-gurmehar-anirudh.png",
    alt: "A couple at an intimate outdoor wedding surrounded by flowers",
  },
  {
    couple: "Jane and Aakash",
    description:
      "A cinematic celebration of love across continents with breathtaking vistas.",
    date: "25 March 2026",
    image: "/images/wedjan/story-jane-aakash.png",
    alt: "A bride and groom sharing a quiet moment in a scenic landscape",
    video: true,
  },
  {
    couple: "Vani and Shivam",
    description:
      "A chic Delhi wedding that beautifully blended tradition with modern style.",
    date: "05 February 2026",
  },
];

export const galleryItems: GalleryItem[] = [
  {
    title: "Bridal Lehenga",
    image: "/images/wedjan/gallery-lehenga.png",
    alt: "A red bridal lehenga displayed beneath an arched window",
    variant: "feature",
    eyebrow: "Trending",
    description: "Explore 500+ designs",
  },
  {
    title: "Outfits",
    image: "/images/wedjan/gallery-outfits.png",
    alt: "A bride in a vivid red outfit seated beneath a floral installation",
    variant: "standard",
  },
  {
    title: "Blouse Designs",
    image: "/images/wedjan/gallery-blouse.png",
    alt: "The embroidered back detail of a bridal blouse",
    variant: "standard",
  },
  {
    title: "Wedding Sarees",
    image: "/images/wedjan/gallery-sarees.png",
    alt: "A bride in a classic red silk wedding saree",
    variant: "wide",
  },
  {
    title: "Mehndi Designs",
    image: "/images/wedjan/gallery-mehndi.png",
    alt: "Detailed traditional mehndi across a bride's palms",
    variant: "square",
  },
];

export const vendors: Vendor[] = [
  {
    name: "Kavitaseth Artistry",
    category: "Bridal Makeup Artists, Andheri East",
    price: "₹ 40,000",
    priceSuffix: "onwards",
    rating: "4.9",
    image: "/images/wedjan/vendor-kavitaseth.png",
    alt: "A bridal makeup artist styling a bride",
  },
  {
    name: "Bellmont Caves",
    category: "Wedding Venues, Ramnagar",
    price: "₹ 2,500",
    priceSuffix: "per plate",
    rating: "4.8",
  },
  {
    name: "Vrindavan Garden",
    category: "Wedding Venues, Koregaon Park",
    price: "₹ 899",
    priceSuffix: "per plate",
    rating: "5.0",
  },
  {
    name: "Royal Studio",
    category: "Wedding Photographers",
    price: "₹ 40,000",
    priceSuffix: "per day",
  },
];
