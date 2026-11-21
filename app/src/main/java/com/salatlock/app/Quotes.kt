package com.salatlock.app

object Quotes {
    val list = listOf(
        "« La prière demeure, pour les croyants, une prescription à des temps déterminés. »\n— Coran, An-Nisâ' 4:103",
        "« Cherchez secours dans l'endurance et la prière. »\n— Coran, Al-Baqara 2:45",
        "« Accomplis la prière, car la prière préserve de la turpitude et du blâmable. »\n— Coran, Al-'Ankabût 29:45",
        "« Préservez les prières, et surtout la prière médiane. »\n— Coran, Al-Baqara 2:238",
        "« Le premier acte sur lequel le serviteur sera jugé le Jour de la Résurrection, c'est la prière. »\n— Hadith rapporté par At-Tirmidhî",
        "« Entre l'homme et la mécréance, il y a l'abandon de la prière. »\n— Hadith rapporté par Muslim",
        "« Les cinq prières effacent les péchés commis entre elles, comme une rivière devant ta porte où tu te laves cinq fois par jour. »\n— Sens d'un hadith rapporté par Al-Bukhârî et Muslim",
        "« L'action la plus aimée d'Allah est la prière accomplie à son heure. »\n— Hadith rapporté par Al-Bukhârî et Muslim",
        "« Celui qui fait bien ses ablutions, ses péchés sortent de son corps, jusque sous ses ongles. »\n— Hadith rapporté par Muslim",
        "« Ô Bilal, appelle à la prière, réconforte-nous par elle. »\n— Hadith rapporté par Abû Dâwûd",
    )

    const val DUA_WUDU_AR = "أَشْهَدُ أَنْ لَا إِلَٰهَ إِلَّا اللهُ وَحْدَهُ لَا شَرِيكَ لَهُ، وَأَشْهَدُ أَنَّ مُحَمَّدًا عَبْدُهُ وَرَسُولُهُ"
    const val DUA_WUDU_FR = "J'atteste qu'il n'y a de divinité qu'Allah, Seul, sans associé, et j'atteste que Muhammad est Son serviteur et Son messager.\n(Invocation après les ablutions — Muslim)"

    fun next(prefs: Prefs): String {
        val i = prefs.quoteIndex % list.size
        prefs.quoteIndex = i + 1
        return list[i]
    }
}
