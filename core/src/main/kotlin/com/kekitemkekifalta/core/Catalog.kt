package com.kekitemkekifalta.core

import com.kekitemkekifalta.core.ClockType.RUNS_OUT
import com.kekitemkekifalta.core.ClockType.SPOILS
import com.kekitemkekifalta.core.Sector.ACOUGUE_FRIOS
import com.kekitemkekifalta.core.Sector.BEBIDAS
import com.kekitemkekifalta.core.Sector.CONGELADOS
import com.kekitemkekifalta.core.Sector.HIGIENE
import com.kekitemkekifalta.core.Sector.HORTIFRUTI
import com.kekitemkekifalta.core.Sector.LATICINIOS
import com.kekitemkekifalta.core.Sector.LIMPEZA
import com.kekitemkekifalta.core.Sector.MERCEARIA
import com.kekitemkekifalta.core.Sector.OUTROS
import com.kekitemkekifalta.core.Sector.PADARIA
import com.kekitemkekifalta.core.Sector.PET

data class CatalogEntry(
    val name: String,
    val sector: Sector,
    val clock: ClockType,
    /** Initial guess of how many days one usual purchase lasts. */
    val days: Double,
    val aliases: List<String> = emptyList(),
) {
    val key: String get() = TextNormalizer.normalize(name)
}

/** Built-in catalog of common items in a Brazilian home. */
object Catalog {
    val entries: List<CatalogEntry> = listOf(
        // Hortifruti
        e("Banana", HORTIFRUTI, SPOILS, 4.0, "bananas"),
        e("Maçã", HORTIFRUTI, SPOILS, 10.0, "maca"),
        e("Laranja", HORTIFRUTI, SPOILS, 10.0),
        e("Limão", HORTIFRUTI, SPOILS, 10.0),
        e("Mamão", HORTIFRUTI, SPOILS, 4.0, "papaia"),
        e("Abacate", HORTIFRUTI, SPOILS, 4.0),
        e("Manga", HORTIFRUTI, SPOILS, 5.0),
        e("Uva", HORTIFRUTI, SPOILS, 5.0),
        e("Morango", HORTIFRUTI, SPOILS, 3.0),
        e("Melancia", HORTIFRUTI, SPOILS, 5.0),
        e("Melão", HORTIFRUTI, SPOILS, 6.0),
        e("Abacaxi", HORTIFRUTI, SPOILS, 4.0),
        e("Pera", HORTIFRUTI, SPOILS, 7.0),
        e("Maracujá", HORTIFRUTI, SPOILS, 7.0),
        e("Tomate", HORTIFRUTI, SPOILS, 6.0),
        e("Cebola", HORTIFRUTI, SPOILS, 21.0),
        e("Alho", HORTIFRUTI, SPOILS, 30.0),
        e("Batata", HORTIFRUTI, SPOILS, 14.0, "batata inglesa"),
        e("Batata-doce", HORTIFRUTI, SPOILS, 14.0),
        e("Cenoura", HORTIFRUTI, SPOILS, 14.0),
        e("Beterraba", HORTIFRUTI, SPOILS, 14.0),
        e("Abobrinha", HORTIFRUTI, SPOILS, 7.0),
        e("Abóbora", HORTIFRUTI, SPOILS, 10.0, "cabotia", "moranga"),
        e("Chuchu", HORTIFRUTI, SPOILS, 7.0),
        e("Pepino", HORTIFRUTI, SPOILS, 7.0),
        e("Pimentão", HORTIFRUTI, SPOILS, 7.0),
        e("Alface", HORTIFRUTI, SPOILS, 4.0),
        e("Couve", HORTIFRUTI, SPOILS, 4.0),
        e("Rúcula", HORTIFRUTI, SPOILS, 3.0),
        e("Repolho", HORTIFRUTI, SPOILS, 14.0),
        e("Brócolis", HORTIFRUTI, SPOILS, 4.0),
        e("Couve-flor", HORTIFRUTI, SPOILS, 5.0),
        e("Cheiro-verde", HORTIFRUTI, SPOILS, 4.0, "salsinha", "cebolinha"),
        e("Coentro", HORTIFRUTI, SPOILS, 4.0),
        e("Mandioca", HORTIFRUTI, SPOILS, 4.0, "aipim", "macaxeira"),
        e("Gengibre", HORTIFRUTI, SPOILS, 21.0),
        e("Ovos", HORTIFRUTI, RUNS_OUT, 14.0, "ovo", "duzia de ovos"),

        // Padaria
        e("Pão francês", PADARIA, SPOILS, 2.0, "pao de sal", "cacetinho", "paozinho"),
        e("Pão de forma", PADARIA, SPOILS, 7.0),
        e("Pão integral", PADARIA, SPOILS, 7.0),
        e("Bisnaguinha", PADARIA, SPOILS, 7.0),
        e("Pão de hambúrguer", PADARIA, SPOILS, 7.0),
        e("Bolo", PADARIA, SPOILS, 4.0),
        e("Torrada", PADARIA, RUNS_OUT, 21.0),

        // Açougue e frios
        e("Carne moída", ACOUGUE_FRIOS, SPOILS, 3.0),
        e("Peito de frango", ACOUGUE_FRIOS, SPOILS, 3.0, "frango", "file de frango"),
        e("Coxa e sobrecoxa", ACOUGUE_FRIOS, SPOILS, 3.0),
        e("Carne bovina", ACOUGUE_FRIOS, SPOILS, 3.0, "bife", "patinho", "alcatra"),
        e("Carne de porco", ACOUGUE_FRIOS, SPOILS, 3.0, "lombo", "bisteca"),
        e("Linguiça", ACOUGUE_FRIOS, SPOILS, 7.0, "linguica toscana"),
        e("Bacon", ACOUGUE_FRIOS, SPOILS, 14.0),
        e("Peixe", ACOUGUE_FRIOS, SPOILS, 2.0, "file de peixe", "tilapia"),
        e("Presunto", ACOUGUE_FRIOS, SPOILS, 5.0),
        e("Peito de peru", ACOUGUE_FRIOS, SPOILS, 5.0),
        e("Muçarela", ACOUGUE_FRIOS, SPOILS, 7.0, "mussarela", "mozarela", "queijo mucarela"),
        e("Mortadela", ACOUGUE_FRIOS, SPOILS, 5.0),
        e("Salame", ACOUGUE_FRIOS, SPOILS, 20.0),
        e("Salsicha", ACOUGUE_FRIOS, SPOILS, 7.0),

        // Laticínios
        e("Leite", LATICINIOS, RUNS_OUT, 7.0, "leite de caixinha"),
        e("Iogurte", LATICINIOS, SPOILS, 7.0),
        e("Queijo minas", LATICINIOS, SPOILS, 6.0, "queijo branco", "frescal"),
        e("Queijo prato", LATICINIOS, SPOILS, 7.0),
        e("Queijo ralado", LATICINIOS, RUNS_OUT, 30.0, "parmesao"),
        e("Requeijão", LATICINIOS, SPOILS, 10.0),
        e("Cream cheese", LATICINIOS, SPOILS, 10.0),
        e("Manteiga", LATICINIOS, RUNS_OUT, 21.0),
        e("Margarina", LATICINIOS, RUNS_OUT, 30.0),
        e("Creme de leite", LATICINIOS, RUNS_OUT, 30.0),
        e("Leite condensado", LATICINIOS, RUNS_OUT, 30.0),
        e("Leite fermentado", LATICINIOS, SPOILS, 7.0, "yakult"),

        // Mercearia
        e("Arroz", MERCEARIA, RUNS_OUT, 30.0),
        e("Feijão", MERCEARIA, RUNS_OUT, 30.0),
        e("Açúcar", MERCEARIA, RUNS_OUT, 30.0),
        e("Sal", MERCEARIA, RUNS_OUT, 90.0),
        e("Café", MERCEARIA, RUNS_OUT, 20.0, "cafe em po"),
        e("Óleo", MERCEARIA, RUNS_OUT, 30.0, "oleo de soja"),
        e("Azeite", MERCEARIA, RUNS_OUT, 45.0),
        e("Macarrão", MERCEARIA, RUNS_OUT, 21.0, "espaguete", "massa"),
        e("Macarrão instantâneo", MERCEARIA, RUNS_OUT, 14.0, "miojo"),
        e("Farinha de trigo", MERCEARIA, RUNS_OUT, 45.0),
        e("Farinha de mandioca", MERCEARIA, RUNS_OUT, 45.0),
        e("Farofa pronta", MERCEARIA, RUNS_OUT, 30.0),
        e("Fubá", MERCEARIA, RUNS_OUT, 45.0),
        e("Aveia", MERCEARIA, RUNS_OUT, 30.0),
        e("Lentilha", MERCEARIA, RUNS_OUT, 45.0),
        e("Grão-de-bico", MERCEARIA, RUNS_OUT, 45.0),
        e("Molho de tomate", MERCEARIA, RUNS_OUT, 10.0),
        e("Extrato de tomate", MERCEARIA, RUNS_OUT, 14.0),
        e("Milho em lata", MERCEARIA, RUNS_OUT, 30.0, "milho verde"),
        e("Ervilha em lata", MERCEARIA, RUNS_OUT, 30.0),
        e("Atum em lata", MERCEARIA, RUNS_OUT, 30.0, "atum"),
        e("Sardinha em lata", MERCEARIA, RUNS_OUT, 30.0, "sardinha"),
        e("Vinagre", MERCEARIA, RUNS_OUT, 60.0),
        e("Maionese", MERCEARIA, RUNS_OUT, 21.0),
        e("Ketchup", MERCEARIA, RUNS_OUT, 30.0),
        e("Mostarda", MERCEARIA, RUNS_OUT, 45.0),
        e("Tempero pronto", MERCEARIA, RUNS_OUT, 30.0, "sazon", "tempero"),
        e("Caldo de galinha", MERCEARIA, RUNS_OUT, 30.0, "caldo knorr"),
        e("Pimenta-do-reino", MERCEARIA, RUNS_OUT, 90.0),
        e("Orégano", MERCEARIA, RUNS_OUT, 90.0),
        e("Biscoito salgado", MERCEARIA, RUNS_OUT, 10.0, "cream cracker", "bolacha"),
        e("Biscoito doce", MERCEARIA, RUNS_OUT, 10.0, "bolacha maria", "biscoito recheado"),
        e("Achocolatado", MERCEARIA, RUNS_OUT, 20.0, "nescau", "toddy"),
        e("Cereal matinal", MERCEARIA, RUNS_OUT, 14.0, "sucrilhos"),
        e("Granola", MERCEARIA, RUNS_OUT, 21.0),
        e("Chocolate", MERCEARIA, RUNS_OUT, 7.0),
        e("Gelatina", MERCEARIA, RUNS_OUT, 30.0),
        e("Fermento", MERCEARIA, RUNS_OUT, 60.0),
        e("Amido de milho", MERCEARIA, RUNS_OUT, 60.0, "maizena"),
        e("Leite em pó", MERCEARIA, RUNS_OUT, 30.0),
        e("Milho de pipoca", MERCEARIA, RUNS_OUT, 30.0, "pipoca"),
        e("Mel", MERCEARIA, RUNS_OUT, 60.0),
        e("Geleia", MERCEARIA, RUNS_OUT, 21.0),
        e("Doce de leite", MERCEARIA, RUNS_OUT, 14.0),
        e("Azeitona", MERCEARIA, RUNS_OUT, 21.0),
        e("Palmito", MERCEARIA, RUNS_OUT, 30.0),
        e("Chá", MERCEARIA, RUNS_OUT, 30.0, "cha"),
        e("Adoçante", MERCEARIA, RUNS_OUT, 45.0),

        // Bebidas
        e("Água mineral", BEBIDAS, RUNS_OUT, 7.0, "agua", "galao"),
        e("Água com gás", BEBIDAS, RUNS_OUT, 14.0),
        e("Refrigerante", BEBIDAS, RUNS_OUT, 7.0, "coca", "guarana"),
        e("Suco de caixinha", BEBIDAS, SPOILS, 5.0, "suco", "suco aberto"),
        e("Suco concentrado", BEBIDAS, RUNS_OUT, 14.0),
        e("Água de coco", BEBIDAS, SPOILS, 5.0),
        e("Cerveja", BEBIDAS, RUNS_OUT, 7.0, "breja"),
        e("Vinho", BEBIDAS, RUNS_OUT, 21.0),

        // Congelados
        e("Pão de queijo congelado", CONGELADOS, RUNS_OUT, 21.0, "pao de queijo"),
        e("Hambúrguer", CONGELADOS, RUNS_OUT, 21.0),
        e("Nuggets", CONGELADOS, RUNS_OUT, 14.0, "empanado"),
        e("Lasanha congelada", CONGELADOS, RUNS_OUT, 14.0, "lasanha"),
        e("Pizza congelada", CONGELADOS, RUNS_OUT, 14.0, "pizza"),
        e("Sorvete", CONGELADOS, RUNS_OUT, 14.0),
        e("Polpa de fruta", CONGELADOS, RUNS_OUT, 14.0, "polpa"),
        e("Batata frita congelada", CONGELADOS, RUNS_OUT, 21.0, "batata palito"),
        e("Legumes congelados", CONGELADOS, RUNS_OUT, 21.0, "seleta"),
        e("Gelo", CONGELADOS, RUNS_OUT, 7.0),

        // Limpeza
        e("Detergente", LIMPEZA, RUNS_OUT, 21.0),
        e("Sabão em pó", LIMPEZA, RUNS_OUT, 30.0),
        e("Sabão líquido", LIMPEZA, RUNS_OUT, 30.0, "lava roupas"),
        e("Amaciante", LIMPEZA, RUNS_OUT, 30.0),
        e("Água sanitária", LIMPEZA, RUNS_OUT, 30.0, "candida", "qboa"),
        e("Desinfetante", LIMPEZA, RUNS_OUT, 30.0, "pinho sol"),
        e("Multiuso", LIMPEZA, RUNS_OUT, 30.0, "veja"),
        e("Limpa-vidros", LIMPEZA, RUNS_OUT, 60.0),
        e("Álcool", LIMPEZA, RUNS_OUT, 45.0, "alcool 70"),
        e("Esponja", LIMPEZA, RUNS_OUT, 14.0, "bucha"),
        e("Palha de aço", LIMPEZA, RUNS_OUT, 30.0, "bombril"),
        e("Saco de lixo", LIMPEZA, RUNS_OUT, 30.0),
        e("Papel toalha", LIMPEZA, RUNS_OUT, 14.0),
        e("Papel alumínio", LIMPEZA, RUNS_OUT, 60.0),
        e("Filme plástico", LIMPEZA, RUNS_OUT, 60.0, "plastico filme"),
        e("Filtro de café", LIMPEZA, RUNS_OUT, 30.0, "coador"),

        // Higiene
        e("Papel higiênico", HIGIENE, RUNS_OUT, 14.0),
        e("Sabonete", HIGIENE, RUNS_OUT, 14.0),
        e("Shampoo", HIGIENE, RUNS_OUT, 30.0, "xampu"),
        e("Condicionador", HIGIENE, RUNS_OUT, 30.0),
        e("Pasta de dente", HIGIENE, RUNS_OUT, 30.0, "creme dental"),
        e("Escova de dente", HIGIENE, RUNS_OUT, 90.0),
        e("Fio dental", HIGIENE, RUNS_OUT, 45.0),
        e("Enxaguante bucal", HIGIENE, RUNS_OUT, 30.0),
        e("Desodorante", HIGIENE, RUNS_OUT, 30.0),
        e("Absorvente", HIGIENE, RUNS_OUT, 30.0),
        e("Lenço umedecido", HIGIENE, RUNS_OUT, 14.0),
        e("Aparelho de barbear", HIGIENE, RUNS_OUT, 30.0, "gilete", "lamina"),
        e("Algodão", HIGIENE, RUNS_OUT, 45.0),
        e("Cotonete", HIGIENE, RUNS_OUT, 60.0, "haste flexivel"),
        e("Hidratante", HIGIENE, RUNS_OUT, 45.0),
        e("Protetor solar", HIGIENE, RUNS_OUT, 60.0),

        // Pet
        e("Ração", PET, RUNS_OUT, 30.0, "racao"),
        e("Sachê para pet", PET, RUNS_OUT, 7.0, "sache"),
        e("Areia de gato", PET, RUNS_OUT, 14.0, "areia"),
        e("Petisco pet", PET, RUNS_OUT, 21.0, "bifinho"),
        e("Tapete higiênico", PET, RUNS_OUT, 21.0),

        // Outros
        e("Pilha", OUTROS, RUNS_OUT, 90.0),
        e("Lâmpada", OUTROS, RUNS_OUT, 180.0),
        e("Guardanapo", OUTROS, RUNS_OUT, 30.0),
        e("Copo descartável", OUTROS, RUNS_OUT, 30.0),
        e("Carvão", OUTROS, RUNS_OUT, 30.0),
        e("Fósforo", OUTROS, RUNS_OUT, 90.0, "isqueiro"),
        e("Vela", OUTROS, RUNS_OUT, 90.0),
    )

    private val byKey: Map<String, CatalogEntry> = entries.associateBy { it.key }

    fun find(name: String): CatalogEntry? {
        val key = TextNormalizer.normalize(name)
        return byKey[key] ?: entries.firstOrNull { entry -> entry.aliases.any { TextNormalizer.normalize(it) == key } }
    }

    private fun e(name: String, sector: Sector, clock: ClockType, days: Double, vararg aliases: String) =
        CatalogEntry(name, sector, clock, days, aliases.toList())
}
