export interface GalleryPhoto {
  id: number;
  /** Thumbnail dimensions, used to reserve space before the image loads. */
  width: number;
  height: number;
  alt: string;
  /** Set for administrator-uploaded photos served by the API instead of the built-in assets. */
  src?: string;
}

export const galleryPhotos: GalleryPhoto[] = [
  { id: 1, width: 640, height: 480, alt: "Guests and their guide smiling inside the SL Tours vehicle" },
  { id: 2, width: 480, height: 640, alt: "Guest exploring the limestone formations of the Cango Caves" },
  { id: 3, width: 480, height: 640, alt: "Guide with guests at a roadside stop" },
  { id: 4, width: 480, height: 640, alt: "Two guests on the coast with the ocean behind them" },
  { id: 5, width: 480, height: 640, alt: "Guests at the colourful Muizenberg beach huts" },
  { id: 6, width: 640, height: 480, alt: "Group selfie with the guide on tour" },
  { id: 7, width: 480, height: 640, alt: "Tour group at a coastal viewpoint" },
  { id: 8, width: 480, height: 640, alt: "Guests walking along a coastal viewpoint" },
  { id: 9, width: 640, height: 480, alt: "Guests at a sunny street stop during a tour" },
  { id: 10, width: 640, height: 480, alt: "Group exploring a roadside attraction" },
  { id: 11, width: 640, height: 480, alt: "Large tour group outside a colourful shop" },
  { id: 12, width: 640, height: 480, alt: "Tour group posing outside a local venue" },
  { id: 13, width: 480, height: 640, alt: "Guide and guests with the mountains behind them" },
  { id: 14, width: 480, height: 640, alt: "Guide with guests beside a statue" },
  { id: 15, width: 480, height: 640, alt: "Guide and guest at the harbour" },
  { id: 16, width: 640, height: 480, alt: "Guest overlooking Hout Bay from Chapman's Peak Drive" },
  { id: 17, width: 480, height: 640, alt: "Couple on the rocky Cape coastline" },
  { id: 18, width: 480, height: 640, alt: "Guests on the balcony of a blue beach hut" },
  { id: 19, width: 480, height: 640, alt: "Group selfie with guests on a sunny day" },
  { id: 20, width: 480, height: 640, alt: "SL Tours branded water bottles ready for guests" },
  { id: 21, width: 640, height: 480, alt: "Group at an ocean lookout" },
  { id: 22, width: 480, height: 640, alt: "Tour group in a garden beneath the mountains" },
  { id: 23, width: 480, height: 640, alt: "Guide with guests in green surroundings" },
  { id: 24, width: 480, height: 640, alt: "Guide selfie with a tour group" },
  { id: 25, width: 480, height: 640, alt: "Guide and guests taking a selfie under a tree" },
  { id: 26, width: 480, height: 640, alt: "Group on a wide sandy beach" },
  { id: 27, width: 640, height: 480, alt: "Large group at a coastal viewpoint" },
  { id: 28, width: 480, height: 640, alt: "Guests enjoying an indoor stop on tour" },
  { id: 29, width: 480, height: 640, alt: "Guests at a seaside village stop" },
  { id: 30, width: 640, height: 480, alt: "Guide at the Cape of Good Hope sign" },
  { id: 31, width: 480, height: 640, alt: "Guide with guests dressed in colourful outfits" },
  { id: 32, width: 480, height: 640, alt: "Guide with a guest on a garden path" },
  { id: 33, width: 480, height: 640, alt: "Guide and guest taking a selfie" },
  { id: 34, width: 480, height: 640, alt: "Open safari vehicle on a game drive" },
  { id: 35, width: 640, height: 480, alt: "Guide with guests beside the tour vehicle" },
  { id: 36, width: 640, height: 480, alt: "Guide with guests at a scenic stop" },
  { id: 37, width: 480, height: 640, alt: "Group on a street lined with colourful houses" },
  { id: 38, width: 480, height: 640, alt: "Guests at a craft stop" },
  { id: 39, width: 480, height: 640, alt: "Group at the Muizenberg beach huts" },
  { id: 40, width: 480, height: 640, alt: "Group on the steps of a beach hut" },
  { id: 41, width: 640, height: 480, alt: "Guide and guests at a mountain viewpoint" },
  { id: 42, width: 531, height: 640, alt: "Bungee jump from the Bloukrans Bridge on the Garden Route" },
  { id: 43, width: 606, height: 640, alt: "African penguins at Boulders Beach" },
  { id: 44, width: 640, height: 438, alt: "The colourful houses of the Bo-Kaap" }
];

export const thumbSrc = (photo: GalleryPhoto) => photo.src ?? `/assets/gallery/${photo.id}-thumb.jpg`;
export const fullSrc = (photo: GalleryPhoto) => photo.src ?? `/assets/gallery/${photo.id}.jpg`;
