package dev.spikeai.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

class SearchCoreTest {
    private fun unit(v: FloatArray): FloatArray { val n = sqrt(v.sumOf { (it * it).toDouble() }).toFloat(); return FloatArray(v.size) { v[it] / n } }

    /** Clustered data (like real photos: many beaches, many dogs) — 5,000 vectors, 512 dims. */
    private fun clustered(n: Int, dim: Int = 512, clusters: Int = 50, rnd: Random = Random(7)): List<FloatArray> {
        val centres = List(clusters) { unit(FloatArray(dim) { rnd.nextFloat() - 0.5f }) }
        return List(n) { val c = centres[rnd.nextInt(clusters)]; unit(FloatArray(dim) { c[it] + (rnd.nextFloat() - 0.5f) * 0.08f }) }
    }

    @Test fun hnswFindsWhatBruteForceFinds() {
        val data = clustered(5000)
        val flat = FlatIndex(); val hnsw = Hnsw(m = 16, efConstruction = 100, ef = 64)
        val t0 = System.nanoTime()
        data.forEachIndexed { i, v -> flat.add(i, v); hnsw.add(i, v) }
        val buildMs = (System.nanoTime() - t0) / 1e6
        val queries = clustered(200, rnd = Random(99))
        var flatNs = 0L
        val truth = queries.map { q -> val a = System.nanoTime(); flat.search(q, 10).map { it.id }.toSet().also { flatNs += System.nanoTime() - a } }
        println("HNSW ${hnsw.stats()}  build %.0f ms  flat %.2f ms/query".format(buildMs, flatNs / 1e6 / queries.size))
        var recallAt200 = 0.0
        for (ef in listOf(16, 32, 64, 128, 200)) {
            hnsw.ef = ef
            var found = 0; var ns = 0L
            queries.forEachIndexed { i, q -> val b = System.nanoTime(); found += hnsw.search(q, 10).count { it.id in truth[i] }; ns += System.nanoTime() - b }
            val recall = found / (queries.size * 10.0)
            println("  ef %3d  recall@10 %.3f  %.2f ms/query".format(ef, recall, ns / 1e6 / queries.size))
            if (ef == 200) recallAt200 = recall
        }
        assertTrue("recall $recallAt200", recallAt200 > 0.95)
    }

    @Test fun tracedHnswReturnsTheSameHits() {
        val data = clustered(2000)
        val hnsw = Hnsw(); data.forEachIndexed { i, v -> hnsw.add(i, v) }
        for (q in clustered(20, rnd = Random(5))) {
            val trace = HnswTrace()
            assertEquals(hnsw.search(q, 10).map { it.id }, hnsw.search(q, 10, trace).map { it.id })
            assertTrue("hops recorded", trace.hops.isNotEmpty())
            assertTrue("compared ${trace.compared} of ${data.size}", trace.compared in 1 until data.size)
        }
    }

    @Test fun bm25ExplainAddsUpToTheScore() {
        val bm = Bm25()
        bm.add(0, "wifi password for the office network"); bm.add(1, "office lunch menu"); bm.add(2, "home wifi wifi router")
        for (hit in bm.search("office wifi password", 10)) {
            assertEquals(hit.score.toDouble(), bm.explain("office wifi password", hit.id).sumOf { it.part }, 1e-5)
        }
    }

    @Test fun bm25PrefersRareExactWords() {
        val bm = Bm25()
        bm.add(1, "Invoice INV-2291 total 1299")
        bm.add(2, "the total of the dinner bill")
        bm.add(3, "holiday photos at the beach")
        assertEquals(1, bm.search("inv 2291", 3).first().id)
        assertEquals(listOf(1, 2), bm.search("total", 3).map { it.id }.sorted())
        assertTrue(bm.search("pizza", 3).isEmpty())
    }

    @Test fun rrfRewardsAgreement() {
        val vec = listOf(Hit(10, .9f), Hit(20, .8f), Hit(30, .7f))
        val kw = listOf(Hit(40, 12f), Hit(20, 9f), Hit(50, 3f))
        val fused = Fusion.rrf(listOf(vec, kw)).map { it.id }
        assertEquals(20, fused.first()) // 2nd in both beats 1st in only one
        assertEquals(setOf(10, 20, 30, 40, 50), fused.toSet())
    }

    @Test fun chunkerPacksWithOverlap() {
        val text = (1..100).joinToString(" ") { "w$it" } + ".\nGST 29ABCDE1234F1Z5"
        val chunks = Chunker(maxWords = 40, overlapWords = 10).chunk(text)
        assertTrue(chunks.all { it.split(" ").size <= 40 })
        assertTrue(chunks[1].startsWith("w31 ")) // last 10 words of chunk 1 repeat
        assertTrue(chunks.last().contains("29ABCDE1234F1Z5"))
    }

    @Test fun probeLearnsAPersonalConceptFromFiveExamples() {
        val rnd = Random(3)
        val mine = unit(FloatArray(512) { rnd.nextFloat() - 0.5f })
        val other = unit(FloatArray(512) { rnd.nextFloat() - 0.5f })
        fun near(c: FloatArray) = unit(FloatArray(512) { c[it] + (rnd.nextFloat() - 0.5f) * 0.15f })
        val probe = LinearProbe(512)
        probe.train(List(5) { near(mine) }, List(50) { near(other) })
        println("probe loss ${probe.lossHistory.first()} → ${probe.lossHistory.last()}")
        val testMine = List(100) { near(mine) }.count { probe.probability(it) > 0.5f }
        val testOther = List(100) { near(other) }.count { probe.probability(it) > 0.5f }
        assertTrue("mine $testMine other $testOther", testMine > 95 && testOther < 5)
    }
}
