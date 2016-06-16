package uk.gov.hmrc.servicereleases

import java.time.LocalDateTime

import org.mockito.Matchers._
import org.mockito.Mockito._
import org.mockito.{Matchers => MockitoMatchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.servicereleases.deployments.{ServiceDeployment, ServiceDeploymentsService}
import uk.gov.hmrc.servicereleases.services.{Repository, ServiceRepositoriesService}
import uk.gov.hmrc.servicereleases.tags.{Tag, TagsService}

import scala.concurrent.Future

class DefaultReleasesServiceSpec extends WordSpec with Matchers with MockitoSugar with ScalaFutures {
  val servicesService = mock[ServiceRepositoriesService]
  val deploymentsService = mock[ServiceDeploymentsService]
  val tagsService = mock[TagsService]
  val repository = mock[ReleasesRepository]

  "updateModel" should {

    val service = new DefaultReleasesService(servicesService, deploymentsService, tagsService, repository)

    "Add any new releases for known services to the mongo repository" in {
      val testData = new TestData()
        .withKnownVersions("1.0.0", "2.0.0").withNewVersions("3.0.0").forService("service")
        .withKnownVersions("1.0.0").withNewVersions("1.1.0").forService("another")
        .setup()

      val result = service.updateModel().futureValue

      testData.verifyReleasesAddedToRepository()
    }

    "Not do anything if there are no new releases" in {
      new TestData()
        .withKnownVersions("1.0.0", "2.0.0").forService("service")
        .withKnownVersions("1.0.0").forService("another")
        .setup()

      val result = service.updateModel().futureValue

      verify(repository, never).add(any())
    }
  }

  private class TestData() {
    private case class ServiceTestData(serviceName: String,
                               expected: Seq[(String, LocalDateTime, LocalDateTime)],
                               knownVersions: Seq[String],
                               unknownVersions: Seq[String])

    private var serviceData: Seq[ServiceTestData] = Seq()

    val now = LocalDateTime.of(2016, 1, 1, 1, 1)
    var expected: Seq[(String, LocalDateTime, LocalDateTime)] = Seq()
    var knownVersions: Seq[String] = List()
    var unknownVersions: Seq[String] = List()

    def withKnownVersions(versions: String*) = {
      knownVersions = versions
      this
    }

    def withNewVersions(versions: String*) = {
      unknownVersions = versions
      this
    }

    def forService(name: String = RandomData.string(8)) = {
      expected = unknownVersions.map { v =>
        val date = RandomData.date()
        (v, date, date.plusHours(1))
      }

      when(tagsService.get("hmrc", name, "github"))
        .thenReturn(Future.successful(
          (knownVersions.map { v => Tag(v, now) } ++
            expected.map { case (v, t, p) => Tag(v, t) }).toList ))

      serviceData = serviceData :+ ServiceTestData(name, expected, knownVersions, unknownVersions)

      expected = Seq()
      knownVersions = List()
      unknownVersions = List()

      this
    }

    def setup() = {
      when(servicesService.getAll()).thenReturn(Future.successful(
        serviceData.map { sd => sd.serviceName -> List(Repository("hmrc", "github")) } toMap ))

      when(deploymentsService.getAll()).thenReturn(Future.successful(
        serviceData.map { sd => sd.serviceName -> buildDeploymentsData(sd) } toMap))

      when(repository.getAll()).thenReturn(Future.successful(
        serviceData.map { sd => sd.serviceName -> sd.knownVersions.map { v => Release(sd.serviceName, v, now, now) } } toMap ))

      when(repository.add(any())).thenReturn(Future.successful(true))

      this
    }

    private def buildDeploymentsData(sd: ServiceTestData): Seq[ServiceDeployment] = {
      sd.knownVersions.map { v => ServiceDeployment(v, now) } ++
        sd.expected.map { case (v, t, p) => ServiceDeployment(v, p) }
    }

    def verifyReleasesAddedToRepository() =
      serviceData.foreach { case sd =>
        sd.expected.foreach { case (v, t, p) => verify(repository).add(Release(sd.serviceName, v, t, p)) } }
  }

}
