/**
 * Decoration of catalog entries. The catalog stores no pictures or cuisines (Spec 2.2), so the app derives a cover
 * and an illustration from the names; anything unknown gets a stable look from a hash of its id.
 */
export interface RestaurantLook {
  cuisine: string
  emoji: string
  /** Tailwind gradient of the cover. */
  cover: string
}

const CUISINES: { match: RegExp; look: RestaurantLook }[] = [
  { match: /burger|grill|bbq/i, look: { cuisine: 'Burgers', emoji: '🍔', cover: 'from-orange-500 via-amber-500 to-yellow-400' } },
  { match: /pizza/i, look: { cuisine: 'Pizza', emoji: '🍕', cover: 'from-red-500 via-orange-500 to-amber-400' } },
  { match: /bistro|trattoria|pasta|ital/i, look: { cuisine: 'Italian', emoji: '🍝', cover: 'from-rose-600 via-red-500 to-orange-400' } },
  { match: /sushi|ramen|wok|asia/i, look: { cuisine: 'Asian', emoji: '🍣', cover: 'from-fuchsia-600 via-rose-500 to-orange-400' } },
  { match: /taco|mex/i, look: { cuisine: 'Mexican', emoji: '🌮', cover: 'from-lime-500 via-emerald-500 to-teal-500' } },
  { match: /salad|green|vegan/i, look: { cuisine: 'Healthy', emoji: '🥗', cover: 'from-emerald-500 via-green-500 to-lime-400' } },
  { match: /cafe|coffee|bakery/i, look: { cuisine: 'Café', emoji: '☕', cover: 'from-amber-700 via-amber-600 to-orange-400' } },
]

const FALLBACKS: RestaurantLook[] = [
  { cuisine: 'Restaurant', emoji: '🍽️', cover: 'from-emerald-600 via-teal-500 to-cyan-400' },
  { cuisine: 'Restaurant', emoji: '🥘', cover: 'from-violet-600 via-purple-500 to-fuchsia-400' },
  { cuisine: 'Restaurant', emoji: '🍲', cover: 'from-sky-600 via-blue-500 to-indigo-400' },
]

const hash = (text: string) => [...text].reduce((h, c) => (h * 31 + c.charCodeAt(0)) >>> 0, 7)

export function restaurantLook(id: string, name: string): RestaurantLook {
  return CUISINES.find((c) => c.match.test(name))?.look ?? FALLBACKS[hash(id) % FALLBACKS.length]
}

const DISHES: [RegExp, string][] = [
  [/burger/i, '🍔'],
  [/fries|chips/i, '🍟'],
  [/soda|cola|drink|juice|lemonade/i, '🥤'],
  [/beer/i, '🍺'],
  [/coffee|latte|espresso/i, '☕'],
  [/pasta|carbonara|spaghetti|lasagna/i, '🍝'],
  [/pizza/i, '🍕'],
  [/tiramisu|cake|dessert|brownie/i, '🍰'],
  [/ice cream|gelato/i, '🍨'],
  [/salad/i, '🥗'],
  [/chicken|wings/i, '🍗'],
  [/taco|burrito/i, '🌮'],
  [/sushi|roll/i, '🍣'],
  [/soup/i, '🍜'],
  [/hot ?dog/i, '🌭'],
]

export function dishEmoji(name: string): string {
  return DISHES.find(([match]) => match.test(name))?.[1] ?? '🍽️'
}
